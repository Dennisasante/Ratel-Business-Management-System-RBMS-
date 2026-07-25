-- V9: Service types become a business-managed list instead of a fixed enum, so
-- each business can add/rename/remove the kinds of service work it offers
-- (installations, revamps, wig-making today, but that set shouldn't be hardcoded).

CREATE TABLE service_types (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_types_business_name UNIQUE (business_id, name)
);

CREATE INDEX idx_service_types_business_id ON service_types(business_id);

-- Seed every existing business with the four types that used to be the fixed
-- enum, so nothing disappears from under anyone already using the feature.
INSERT INTO service_types (business_id, name)
SELECT id, unnest(ARRAY['Installation', 'Revamp', 'Wig Making', 'Other']) FROM businesses;

ALTER TABLE service_catalog ADD COLUMN service_type_id UUID REFERENCES service_types(id) ON DELETE SET NULL;
ALTER TABLE service_orders ADD COLUMN service_type_id UUID REFERENCES service_types(id) ON DELETE SET NULL;

UPDATE service_catalog sc SET service_type_id = st.id
FROM service_types st
WHERE st.business_id = sc.business_id AND st.name = (
    CASE sc.type
        WHEN 'INSTALLATION' THEN 'Installation'
        WHEN 'REVAMP' THEN 'Revamp'
        WHEN 'WIG_MAKING' THEN 'Wig Making'
        ELSE 'Other'
    END
);

UPDATE service_orders so SET service_type_id = st.id
FROM service_types st
WHERE st.business_id = so.business_id AND st.name = (
    CASE so.type
        WHEN 'INSTALLATION' THEN 'Installation'
        WHEN 'REVAMP' THEN 'Revamp'
        WHEN 'WIG_MAKING' THEN 'Wig Making'
        ELSE 'Other'
    END
);

ALTER TABLE service_catalog ALTER COLUMN service_type_id SET NOT NULL;
ALTER TABLE service_orders ALTER COLUMN service_type_id SET NOT NULL;

ALTER TABLE service_catalog DROP COLUMN type;
ALTER TABLE service_orders DROP COLUMN type;

CREATE INDEX idx_service_orders_service_type_id ON service_orders(business_id, service_type_id);
