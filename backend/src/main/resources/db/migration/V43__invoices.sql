-- V43: Standalone invoices — freeform, not tied to a Sale/Service Order.
-- invoice_number is assigned in code (InvoiceService, under a row lock on
-- the business), not DB-generated, since it needs to be sequential PER
-- BUSINESS (unlike sale_number/order_number, which are a shared BIGSERIAL) —
-- a customer-facing invoice looking like "Invoice #4127" on a business's
-- very first invoice would look wrong.
CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    invoice_number  BIGINT NOT NULL,
    customer_id     UUID REFERENCES customers(id) ON DELETE SET NULL,
    -- Snapshotted, not just joined through customer_id — covers a client who
    -- isn't in the Customer list at all, and keeps a sent invoice's "Bill To"
    -- block correct even if the customer is later renamed/deleted.
    customer_name    VARCHAR(150),
    customer_email   VARCHAR(150),
    customer_phone   VARCHAR(50),
    customer_address VARCHAR(300),
    issue_date      DATE NOT NULL,
    due_date        DATE,
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, SENT, PAID, OVERDUE
    subtotal        NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (business_id, invoice_number)
);

CREATE TABLE invoice_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description     TEXT NOT NULL,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12,2) NOT NULL,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal        NUMERIC(12,2) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_invoices_business_id ON invoices(business_id);
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoice_items_invoice_id ON invoice_items(invoice_id);
