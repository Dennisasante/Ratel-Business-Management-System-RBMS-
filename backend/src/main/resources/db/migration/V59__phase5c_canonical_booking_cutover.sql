-- Talia Unified Platform — Phase 5C: canonical Booking cutover, per-business lifecycle, package
-- content backfill support, and the immutable historical transaction snapshot — frozen design per
-- "Phase 5C Design — Revision 4" (reviewed and approved). Purely additive: no existing table or
-- constraint (V1-V58) is altered beyond the two brand-new composite-FK-target unique constraints
-- below, which are formalities the new FKs require (service_orders.id is already globally unique),
-- not behaviour changes to anything that reads these tables today.
--
-- LEGACY_RETIREMENT_READY / LEGACY_RETIRED are modeled in the CHECK constraint for lifecycle
-- completeness only — no Phase 5C code transitions any business into either state (Revision 4 §6).

-- ==================== Per-business cutover lifecycle ====================

ALTER TABLE businesses
    ADD COLUMN booking_cutover_state VARCHAR(30) NOT NULL DEFAULT 'NOT_READY';

ALTER TABLE businesses
    ADD CONSTRAINT chk_businesses_booking_cutover_state CHECK (
        booking_cutover_state IN (
            'NOT_READY', 'VERIFIED', 'CANONICAL_ENABLED', 'CANONICAL_DATA_INVALID',
            'LEGACY_RETIREMENT_READY', 'LEGACY_RETIRED'
        )
    );

-- ==================== Package backfill support ====================
--
-- display_order: no existing column captures original ServicePackageItem entry sequence (that
-- table has neither an ordering column nor a created_at timestamp) — this is assigned
-- deterministically (by legacy item id) at backfill time so canonical display order is at least
-- stable/reproducible across reruns, not a reconstruction of a sequence the legacy schema never
-- captured in the first place.
--
-- legacy_service_package_item_id: the backfill's own idempotency key (Revision 4 §3/§6) — lets a
-- rerun find and reconcile the SAME canonical component rather than duplicating it. Nullable:
-- populated only on components created by the Phase 5C package backfill; a future
-- customer-configurable component (not part of this phase) would leave it null.
ALTER TABLE package_components
    ADD COLUMN display_order INT NOT NULL DEFAULT 0,
    ADD COLUMN legacy_service_package_item_id UUID;

-- Tenant + package ownership are both structurally part of this identity, per Revision 4 §3 —
-- business_id and offering_id are both already columns on this same row (offering_id transitively
-- identifies the package, since a PackageComponent belongs to exactly one Offering).
CREATE UNIQUE INDEX uq_package_components_legacy_item_mapping
    ON package_components (business_id, offering_id, legacy_service_package_item_id)
    WHERE legacy_service_package_item_id IS NOT NULL;

CREATE INDEX idx_package_components_legacy_item_id ON package_components (legacy_service_package_item_id);

-- ==================== ServiceOrder canonical tracking pointer ====================
--
-- Composite-FK target — same formality V57 already applied to service_catalog/service_packages;
-- service_orders.id is already globally unique, so pairing it with business_id here is additive.
ALTER TABLE service_orders
    ADD CONSTRAINT uq_service_orders_business_id_id UNIQUE (business_id, id);

-- Nullable, audit/tracking pointer only — never re-read for historical pricing/display (Revision 4
-- §2/§13). ON DELETE SET NULL (never RESTRICT, never CASCADE): a deleted Offering must never block
-- or corrupt an already-persisted ServiceOrder — see the class comment on service_order_line_snapshots
-- below for the full reasoning, which applies identically here.
ALTER TABLE service_orders
    ADD COLUMN offering_id UUID;

ALTER TABLE service_orders
    ADD CONSTRAINT fk_service_orders_offering FOREIGN KEY (business_id, offering_id)
        REFERENCES offerings(business_id, id) ON DELETE SET NULL;

CREATE INDEX idx_service_orders_offering_id ON service_orders (offering_id);

-- ==================== Historical transaction snapshot (engine-agnostic) ====================
--
-- Deliberately NOT a persisted copy of PricingResult/PricingAdjustment (Phase 5B's own pricing-
-- engine diagnostic shape) — this table is the commercial record: what was actually charged and
-- for what, in a shape any future pricing model (today's default/substitution, or a later fully
-- configurable one) can always produce. label/amount/quantity are the immutable commercial facts;
-- source_component_id/source_option_id are nullable traceability pointers only (Revision 4 §2,
-- Model A) — a later deletion of the Offering/PackageComponent/Option this line was priced against
-- must never rewrite or block access to what was actually charged.
CREATE TABLE service_order_line_snapshots (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id          UUID NOT NULL,
    service_order_id     UUID NOT NULL,
    label                VARCHAR(150) NOT NULL,
    amount               NUMERIC(12,2) NOT NULL,
    quantity             INT,
    source_component_id  UUID,
    source_option_id     UUID,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- RESTRICT is correct here (unlike the two SET NULL pointers below): a correction/adjustment
    -- record (future, out-of-scope phase) always references a service_order_id, so the order
    -- itself must never be deletable out from under its own line snapshots. No existing service
    -- deletes a ServiceOrder today (archive-only, matching every other transaction entity in this
    -- codebase), so this is a defensive backstop, not a live behaviour change.
    CONSTRAINT fk_service_order_line_snapshots_order FOREIGN KEY (business_id, service_order_id)
        REFERENCES service_orders(business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_service_order_line_snapshots_component FOREIGN KEY (business_id, source_component_id)
        REFERENCES package_components(business_id, id) ON DELETE SET NULL,
    CONSTRAINT fk_service_order_line_snapshots_option FOREIGN KEY (business_id, source_option_id)
        REFERENCES options(business_id, id) ON DELETE SET NULL
);
CREATE INDEX idx_service_order_line_snapshots_business_id ON service_order_line_snapshots (business_id);
CREATE INDEX idx_service_order_line_snapshots_order_id ON service_order_line_snapshots (service_order_id);

-- Append-only enforcement at the database level (Revision 4 §2) — "application code never updates
-- it" was explicitly rejected as insufficient. The one narrow exception carved out is the FK
-- ON DELETE SET NULL actions above, which fire as an ordinary UPDATE touching ONLY the two
-- traceability pointer columns; every other UPDATE, and every DELETE, is rejected outright. A
-- genuine correction is a future, separate, additive record (service_order_line_corrections,
-- explicitly out of scope for Phase 5C) that references the original row — it is never edited or
-- removed here.
CREATE FUNCTION reject_snapshot_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.label IS NOT DISTINCT FROM OLD.label
       AND NEW.amount IS NOT DISTINCT FROM OLD.amount
       AND NEW.quantity IS NOT DISTINCT FROM OLD.quantity
       AND NEW.service_order_id = OLD.service_order_id
       AND NEW.business_id = OLD.business_id
       AND NEW.created_at = OLD.created_at
    THEN
        RETURN NEW; -- allow only a pointer-nulling update (source_component_id/source_option_id)
    END IF;
    RAISE EXCEPTION 'service_order_line_snapshots rows are immutable except for FK-driven traceability nulling';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_snapshot_append_only
    BEFORE UPDATE OR DELETE ON service_order_line_snapshots
    FOR EACH ROW EXECUTE FUNCTION reject_snapshot_mutation();
