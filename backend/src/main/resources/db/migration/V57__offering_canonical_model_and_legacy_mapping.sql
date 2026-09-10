-- Talia Unified Platform — Phase 5A: canonical Offering identity/retirement (`active`), booking
-- configuration (`offering_booking_config`), Option→Offering references, and explicit, typed
-- legacy mapping — frozen design per "Phase 5A — Final Implementation Specification" (reviewed
-- and approved). Purely additive: no existing table (including V53-V56) is altered beyond the two
-- brand-new composite-FK-target constraints on the legacy tables below; nothing here is read by
-- any existing transaction-creating service yet — Offering remains non-load-bearing this phase.
--
-- Coexistence model (see ServiceCatalogService/ServicePackageService): the legacy tables
-- (service_catalog, service_packages) remain the only user-editable surface. Every write to them
-- is synchronized, in the SAME @Transactional method, to the linked Offering/offering_booking_config
-- row below — never a second, independently-editable admin surface, never a background
-- reconciliation job. PackageComponent/Option population for EXISTING packages is deliberately
-- deferred to Phase 5B (approved decision) — a PACKAGE Offering created by this phase's backfill
-- is structurally valid but has zero package_components until then.

-- ==================== Offering: identity/retirement + explicit legacy mapping ====================

ALTER TABLE offerings
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN legacy_service_catalog_id UUID,
    ADD COLUMN legacy_service_package_id UUID;

-- Explicit, typed, deterministic mapping — never name/heuristic matching. Both null is a valid
-- "no legacy counterpart" state (e.g. a future ROOM_TYPE/EVENT_PACKAGE); at most one may be set.
ALTER TABLE offerings
    ADD CONSTRAINT chk_offerings_legacy_mutually_exclusive
        CHECK (legacy_service_catalog_id IS NULL OR legacy_service_package_id IS NULL),
    ADD CONSTRAINT uq_offerings_legacy_service_catalog_id UNIQUE (legacy_service_catalog_id),
    ADD CONSTRAINT uq_offerings_legacy_service_package_id UNIQUE (legacy_service_package_id);

-- Required composite-FK targets on the two LEGACY tables — the first time this project has added
-- a constraint to a pre-existing (pre-Phase-2) table rather than only to new ones. Purely
-- additive: `id` is already each table's own globally-unique primary key, so pairing it with
-- business_id in a named UNIQUE constraint is a formality the FK below requires, not a behavior
-- change to anything that reads these tables today.
ALTER TABLE service_catalog
    ADD CONSTRAINT uq_service_catalog_business_id_id UNIQUE (business_id, id);
ALTER TABLE service_packages
    ADD CONSTRAINT uq_service_packages_business_id_id UNIQUE (business_id, id);

-- ON DELETE RESTRICT (not CASCADE, and NOT a change to any existing V54 FK — these are two brand
-- new constraints on two brand new columns): legacy items are already archive-only (setActive(),
-- no delete endpoint exists on either legacy entity) so this is a defense-in-depth backstop for a
-- path that shouldn't occur, not a live behavior change — a hard delete of a mapped legacy row
-- must fail loudly rather than silently orphan the canonical side, same reasoning as Phase 3's
-- own policy chain.
ALTER TABLE offerings
    ADD CONSTRAINT fk_offerings_legacy_service_catalog FOREIGN KEY (business_id, legacy_service_catalog_id)
        REFERENCES service_catalog(business_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_offerings_legacy_service_package FOREIGN KEY (business_id, legacy_service_package_id)
        REFERENCES service_packages(business_id, id) ON DELETE RESTRICT;

-- ==================== Offering booking configuration (1:1, scheduling-specific only) ====================
--
-- Split from Offering itself: `active` is identity-level (meaningful to a plain Sale too, which
-- never cares about scheduling), everything here is meaningful only to the booking/scheduling use
-- case — mirrors this codebase's own existing Business/BusinessWorkingHours 1:1-optional-config
-- precedent. requires_location is a REAL column here for PACKAGE offerings too, closing the
-- legacy ServicePackage asymmetry (BookingService hardcodes false for packages today) — see the
-- backfill's own comment below for why the backfilled VALUE still defaults to false.
CREATE TABLE offering_booking_config (
    offering_id              UUID PRIMARY KEY,
    business_id              UUID NOT NULL,
    bookable_online          BOOLEAN NOT NULL DEFAULT FALSE,
    duration_minutes         INT NOT NULL DEFAULT 30,
    max_concurrent_bookings  INT NOT NULL DEFAULT 1,
    requires_location        BOOLEAN NOT NULL DEFAULT FALSE,
    -- NONE/DEPOSIT/FULL, or null to fall back to the business's own default
    -- (BusinessIntegrations.bookingPaymentPolicy) — identical fallback semantics to the legacy
    -- paymentPolicyOverride columns this mirrors.
    payment_policy_override  VARCHAR(20),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_offering_booking_config_offering FOREIGN KEY (business_id, offering_id)
        REFERENCES offerings(business_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_offering_booking_config_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT chk_offering_booking_config_capacity_positive CHECK (max_concurrent_bookings > 0),
    CONSTRAINT chk_offering_booking_config_payment_policy
        CHECK (payment_policy_override IS NULL OR payment_policy_override IN ('NONE', 'DEPOSIT', 'FULL'))
);
CREATE INDEX idx_offering_booking_config_business_id ON offering_booking_config(business_id);

-- ==================== Option → Offering ====================
--
-- Lets a SELECTION option point at a real, independently-priced/tracked Offering (reproducing
-- ServicePackageItem's own job — pointing a package slot at a real catalog service — without
-- redefining that service). Nullable: an Option with no offering_id keeps its existing standalone
-- label/price-modifier capability unchanged (e.g. Custom Wig-style modifiers with no catalog
-- counterpart). "Only SERVICE-type Offerings may be referenced" is deliberately NOT a DB
-- constraint — Phase 2 stayed trigger-free for this exact class of cross-table invariant (see
-- V54's own comment on component_kind/quantity_rules); enforced at the service layer instead,
-- proven by test.
ALTER TABLE options
    ADD COLUMN offering_id UUID;

ALTER TABLE options
    ADD CONSTRAINT fk_options_offering FOREIGN KEY (business_id, offering_id)
        REFERENCES offerings(business_id, id) ON DELETE RESTRICT;

CREATE INDEX idx_options_offering_id ON options(offering_id);
