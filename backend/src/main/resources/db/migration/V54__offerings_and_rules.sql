-- Talia Unified Platform — Phase 2: the generic Offering / PackageComponent / Option /
-- SubstitutionRule / QuantityRule model (architecture proposal §F), reconciled across three
-- review rounds before implementation:
--
--   - business_id is propagated onto every table and enforced via composite foreign keys —
--     (business_id, parent_id) REFERENCES parent(business_id, id) — so a cross-business
--     reference is a constraint violation the database itself refuses, never just an
--     application-layer bug a repository method forgot to check. This is new precedent for
--     this codebase (no existing migration uses composite FKs); the existing single-column
--     UNIQUE(business_id, ...) constraints elsewhere were NOT extended to add business_id here,
--     because every child id below already functionally determines its own business_id via
--     this same composite-FK chain — adding it would pad the constraint, not strengthen it.
--     The one exception, deliberately NOT closed in this phase: two standalone
--     (component_id NULL) options in the same business could share a label, since Postgres
--     treats every NULL as distinct in a UNIQUE index. A partial unique index would close it;
--     none of this phase's worked examples need it, so it isn't added speculatively.
--
--   - package_components.component_kind makes explicit that a "slot" and a "quantity anchor"
--     are structurally different uses of the same attachment-point table — mirrors this
--     project's own sale_items.item_type precedent (chk_sale_items_item_type). SELECTION-kind
--     rows are governed by required/min_selections/max_selections over child `options` rows —
--     one real invariant covering a mandatory single pick (min=1,max=1), an optional multi-pick
--     (min=0,max=NULL), and an optional single toggle (min=0,max=1), differing only in values,
--     not structure. QUANTITY-kind rows (e.g. a birthday package's guest count) are governed
--     entirely by a sibling quantity_rules row instead; chk_package_components_quantity_kind_bounds
--     refuses a QUANTITY-kind row that also carries SELECTION-kind values. Whether a QUANTITY-kind
--     component actually HAS a matching quantity_rules row (and a SELECTION-kind one does NOT) is
--     not enforced here — that needs a cross-table check Postgres can't express without a
--     trigger, and this phase stays trigger-free; a future service layer must enforce it.
--
-- Purely additive. No existing table is altered. Nothing here is read by any existing service,
-- controller, or frontend file. No ServicePackage/CustomWigRequest cutover — that stays a
-- separate, later, separately-approved phase.

CREATE TABLE offerings (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    type         VARCHAR(20) NOT NULL,
    name         VARCHAR(150) NOT NULL,
    base_price   NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_offerings_type CHECK (type IN ('PRODUCT','SERVICE','PACKAGE','ROOM_TYPE','EVENT_PACKAGE')),
    -- Composite-FK target for package_components below. No name-uniqueness constraint —
    -- matches service_packages' own existing precedent (V16), which Offering generalizes.
    CONSTRAINT uq_offerings_business_id_id UNIQUE (business_id, id)
);
CREATE INDEX idx_offerings_business_id ON offerings(business_id);

CREATE TABLE package_components (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id      UUID NOT NULL,
    offering_id      UUID NOT NULL,
    slot_name        VARCHAR(100) NOT NULL,
    component_kind   VARCHAR(20) NOT NULL DEFAULT 'SELECTION',
    required         BOOLEAN NOT NULL DEFAULT FALSE,
    min_selections   INT NOT NULL DEFAULT 0,
    max_selections   INT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_package_components_offering FOREIGN KEY (business_id, offering_id)
        REFERENCES offerings(business_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_package_components_business_id_id UNIQUE (business_id, id),
    CONSTRAINT uq_package_components_offering_slot UNIQUE (offering_id, slot_name),
    CONSTRAINT chk_package_components_kind CHECK (component_kind IN ('SELECTION','QUANTITY')),
    CONSTRAINT chk_package_components_min_nonneg CHECK (min_selections >= 0),
    CONSTRAINT chk_package_components_max_ge_min CHECK (max_selections IS NULL OR max_selections >= min_selections),
    CONSTRAINT chk_package_components_quantity_kind_bounds CHECK (
        component_kind <> 'QUANTITY' OR (min_selections = 0 AND max_selections IS NULL AND required = FALSE)
    )
);
CREATE INDEX idx_package_components_offering_id ON package_components(offering_id);
CREATE INDEX idx_package_components_business_id ON package_components(business_id);

CREATE TABLE options (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Direct FK to businesses (not just the composite FK to package_components below) —
    -- deliberate, so a standalone option (component_id NULL) still has unambiguous ownership.
    -- MATCH SIMPLE (Postgres's default) skips a multi-column FK entirely when any of its
    -- referencing columns is NULL, so fk_options_component alone would leave a standalone
    -- option's business_id unvalidated against anything.
    business_id            UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    component_id           UUID,
    label                  VARCHAR(100) NOT NULL,
    -- Signed, unconstrained — mirrors CustomItemAttributeOption.priceModifier's own existing
    -- precedent; a downgrade/discount option is a legitimate case.
    price_adjustment       NUMERIC(12,2) NOT NULL DEFAULT 0,
    requires_manual_quote  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_options_component FOREIGN KEY (business_id, component_id)
        REFERENCES package_components(business_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_options_business_id_id UNIQUE (business_id, id),
    -- NULL component_id rows are exempt from this uniqueness by ordinary SQL NULL semantics —
    -- see the header comment on standalone-option labels.
    CONSTRAINT uq_options_component_label UNIQUE (component_id, label)
);
CREATE INDEX idx_options_component_id ON options(component_id);
CREATE INDEX idx_options_business_id ON options(business_id);

CREATE TABLE substitution_rules (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    from_option_id  UUID NOT NULL,
    to_option_id    UUID NOT NULL,
    -- Signed, unconstrained — same reasoning as options.price_adjustment.
    price_delta     NUMERIC(12,2) NOT NULL DEFAULT 0,
    max_count       INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Both options are independently anchored to this row's OWN business_id, which
    -- transitively guarantees they belong to the same business as each other — without ever
    -- comparing the two option rows directly.
    CONSTRAINT fk_substitution_rules_from_option FOREIGN KEY (business_id, from_option_id)
        REFERENCES options(business_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_substitution_rules_to_option FOREIGN KEY (business_id, to_option_id)
        REFERENCES options(business_id, id) ON DELETE CASCADE,
    CONSTRAINT uq_substitution_rules_pair UNIQUE (from_option_id, to_option_id),
    CONSTRAINT chk_substitution_rules_not_self CHECK (from_option_id <> to_option_id),
    CONSTRAINT chk_substitution_rules_max_count_positive CHECK (max_count >= 1)
);
CREATE INDEX idx_substitution_rules_from_option_id ON substitution_rules(from_option_id);
CREATE INDEX idx_substitution_rules_to_option_id ON substitution_rules(to_option_id);
CREATE INDEX idx_substitution_rules_business_id ON substitution_rules(business_id);

CREATE TABLE quantity_rules (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id       UUID NOT NULL,
    component_id      UUID NOT NULL,
    min_quantity      INT NOT NULL DEFAULT 0,
    max_quantity      INT,
    extra_unit_price  NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_quantity_rules_component FOREIGN KEY (business_id, component_id)
        REFERENCES package_components(business_id, id) ON DELETE CASCADE,
    -- One quantity rule per component — a deliberate Phase 2 simplification. A component
    -- needing tiered/multi-band quantity pricing is future scope, not one of the worked examples.
    CONSTRAINT uq_quantity_rules_component UNIQUE (component_id),
    CONSTRAINT chk_quantity_rules_min_nonneg CHECK (min_quantity >= 0),
    CONSTRAINT chk_quantity_rules_max_ge_min CHECK (max_quantity IS NULL OR max_quantity >= min_quantity)
);
CREATE INDEX idx_quantity_rules_component_id ON quantity_rules(component_id);
CREATE INDEX idx_quantity_rules_business_id ON quantity_rules(business_id);
