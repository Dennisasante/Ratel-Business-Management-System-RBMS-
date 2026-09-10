-- Talia Unified Platform — Phase 5B: the single schema addition approved in the frozen Phase 5B
-- specification (Revision 2) — package_components.default_option_id. Purely additive: no
-- existing table/constraint (including every V54/V57 constraint) is altered.
--
-- A default option is only meaningful when exactly one option is guaranteed selected — a
-- same-row CHECK (no cross-table lookup needed) enforces that: default_option_id may only be set
-- on a component that is both required AND capped at exactly one selection. QUANTITY-kind
-- components are transitively excluded too, since chk_package_components_quantity_kind_bounds
-- already forces required=FALSE and max_selections=NULL on every QUANTITY-kind row.
--
-- Whether default_option_id actually names an Option belonging to THIS SAME component (not some
-- other component, even under the same business) is a cross-table fact Postgres can't express
-- without a trigger — matching this schema's own established, deliberate trigger-free posture for
-- this class of invariant (see package_components' own class-level precedent on component_kind/
-- QuantityRule agreement). PackagePricingService enforces it defensively at calculation time
-- (CONFIGURATION_INCONSISTENT), per the frozen specification.

ALTER TABLE package_components
    ADD COLUMN default_option_id UUID;

ALTER TABLE package_components
    ADD CONSTRAINT chk_package_components_default_option_requires_single_required
        CHECK (default_option_id IS NULL OR (required = TRUE AND max_selections = 1)),
    ADD CONSTRAINT fk_package_components_default_option FOREIGN KEY (business_id, default_option_id)
        REFERENCES options(business_id, id) ON DELETE RESTRICT;

CREATE INDEX idx_package_components_default_option_id ON package_components(default_option_id);
