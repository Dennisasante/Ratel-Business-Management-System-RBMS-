-- V7: Suppliers/Purchase Orders, staff commissions, and second-Super-Admin support.

CREATE TABLE suppliers (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(150),
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE purchase_orders (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    po_number       BIGSERIAL,
    supplier_id     UUID REFERENCES suppliers(id) ON DELETE SET NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, RECEIVED, CANCELLED
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_at     TIMESTAMPTZ
);

CREATE TABLE purchase_order_items (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    purchase_order_id   UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id          UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name        VARCHAR(150) NOT NULL,
    unit_cost           NUMERIC(12,2) NOT NULL,
    quantity            INTEGER NOT NULL CHECK (quantity > 0),
    subtotal            NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_suppliers_business_id ON suppliers(business_id);
CREATE INDEX idx_purchase_orders_business_id ON purchase_orders(business_id);
CREATE INDEX idx_purchase_order_items_po_id ON purchase_order_items(purchase_order_id);

-- Staff commissions: a percentage rate per user, and the commission amount
-- earned on each sale is snapshotted onto the sale itself at creation time —
-- so a later rate change never retroactively alters what someone already earned.
ALTER TABLE users ADD COLUMN commission_rate NUMERIC(5,2) NOT NULL DEFAULT 0;
ALTER TABLE sales ADD COLUMN commission_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Lets a second Super Admin be created the same way staff are: an existing
-- admin sets a temporary password, and the new admin is forced to change it.
ALTER TABLE platform_admins ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
