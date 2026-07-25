-- V2: Inventory module — products and their stock movement history.
-- Follows the same tenant pattern as V1: every table carries business_id,
-- and every query in the repository layer is scoped by it.

CREATE TABLE products (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name                VARCHAR(150) NOT NULL,
    category            VARCHAR(100),
    sku                 VARCHAR(100),
    cost_price          NUMERIC(12,2) NOT NULL DEFAULT 0,
    selling_price       NUMERIC(12,2) NOT NULL DEFAULT 0,
    quantity            INTEGER      NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    low_stock_threshold INTEGER      NOT NULL DEFAULT 5,
    supplier_name       VARCHAR(150),
    image_url           VARCHAR(500),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_business_sku UNIQUE (business_id, sku)
);

-- Every stock change (add / remove / adjust) is logged here rather than just
-- overwriting products.quantity, so "Opening Stock: 100, Sold: 35, Remaining: 65"
-- style history (from the spec) is queryable per product.
CREATE TABLE stock_movements (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    movement_type       VARCHAR(20) NOT NULL, -- ADD, REMOVE, ADJUST
    quantity_change     INTEGER     NOT NULL, -- signed: +20, -5, etc.
    resulting_quantity  INTEGER     NOT NULL, -- product quantity after this movement
    note                VARCHAR(255),
    created_by          UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_business_id ON products(business_id);
CREATE INDEX idx_stock_movements_business_id ON stock_movements(business_id);
CREATE INDEX idx_stock_movements_product_id ON stock_movements(product_id);
