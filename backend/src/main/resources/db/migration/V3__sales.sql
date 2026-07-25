-- V3: Customers (lightweight CRM) + Sales/POS.
-- Same tenant pattern: every table carries business_id, sale_items also
-- denormalizes business_id so it can be queried/secured without a join.

CREATE TABLE customers (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    full_name   VARCHAR(150) NOT NULL,
    phone       VARCHAR(50),
    email       VARCHAR(150),
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sales (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    sale_number     BIGSERIAL,                 -- friendly display number, e.g. "Sale #1024"
    customer_id     UUID REFERENCES customers(id) ON DELETE SET NULL,
    cashier_id      UUID REFERENCES users(id) ON DELETE SET NULL,
    payment_method  VARCHAR(30) NOT NULL,       -- CASH, MOBILE_MONEY, CARD, BANK_TRANSFER
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sale_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    sale_id         UUID NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id      UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name    VARCHAR(150) NOT NULL,      -- snapshot: keeps the receipt correct even if the product is renamed/deleted later
    unit_price      NUMERIC(12,2) NOT NULL,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    subtotal        NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_customers_business_id ON customers(business_id);
CREATE INDEX idx_sales_business_id ON sales(business_id);
CREATE INDEX idx_sales_customer_id ON sales(customer_id);
CREATE INDEX idx_sale_items_business_id ON sale_items(business_id);
CREATE INDEX idx_sale_items_sale_id ON sale_items(sale_id);
