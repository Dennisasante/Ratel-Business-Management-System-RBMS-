-- V8: Service orders (installations/revamps/wig-making), their fixed-price catalog,
-- and real product categories to replace the free-text column on products.

-- Fixed-price catalog for services (installs/revamps/wig-making "standard" prices).
-- Kept separate from products; an order's price always starts here but is
-- copied onto the order and is editable per-order from that point on.
CREATE TABLE service_catalog (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    type         VARCHAR(20) NOT NULL, -- INSTALLATION, REVAMP, WIG_MAKING, OTHER
    name         VARCHAR(150) NOT NULL,
    price        NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE service_orders (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    order_number        BIGSERIAL,                 -- "Order #204" — same pattern as sale_number/po_number
    type                VARCHAR(20) NOT NULL,       -- INSTALLATION, REVAMP, WIG_MAKING, OTHER
    status              VARCHAR(20) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, IN_PROGRESS, COMPLETED, PICKED_UP, CANCELLED
    customer_id         UUID REFERENCES customers(id) ON DELETE SET NULL,  -- reuse existing customers table
    service_catalog_id  UUID REFERENCES service_catalog(id) ON DELETE SET NULL, -- which catalog item this priced from, if any
    notes               TEXT,                       -- what's wrong / what's needed
    price               NUMERIC(12,2) NOT NULL DEFAULT 0,   -- editable, pre-filled from catalog
    assigned_staff_id   UUID REFERENCES users(id) ON DELETE SET NULL, -- tracking only, no commission math
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(), -- the two timestamps flagged as most important
    picked_up_at         TIMESTAMPTZ,
    ready_email_sent_at TIMESTAMPTZ,                 -- null until the "ready" email has gone out at least once
    created_by          UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_catalog_business_id ON service_catalog(business_id);
CREATE INDEX idx_service_orders_business_id ON service_orders(business_id);
CREATE INDEX idx_service_orders_status ON service_orders(business_id, status);
CREATE INDEX idx_service_orders_type ON service_orders(business_id, type);
CREATE INDEX idx_service_orders_customer_id ON service_orders(customer_id);

-- Inventory: real categories instead of a free-text column.
CREATE TABLE product_categories (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_categories_business_name UNIQUE (business_id, name)
);

ALTER TABLE products ADD COLUMN category_id UUID REFERENCES product_categories(id) ON DELETE SET NULL;
-- Backfill: turn existing free-text `category` values into real rows, then point products at them.
INSERT INTO product_categories (business_id, name)
SELECT DISTINCT business_id, category FROM products WHERE category IS NOT NULL AND category <> '';
UPDATE products p SET category_id = pc.id
FROM product_categories pc
WHERE pc.business_id = p.business_id AND pc.name = p.category;
-- Old free-text column kept for one release as a safety net, then dropped in V9.
