ALTER TABLE business_integrations ADD COLUMN payment_gateway VARCHAR(20) NOT NULL DEFAULT 'PAYSTACK';

-- One row per payment event across the business, regardless of which flow it came
-- from (service order, sale, booking, purchase order) or whether it went through a
-- gateway at all (a manual cash record is gateway='MANUAL'). This is the "every
-- transaction" ledger — SubscriptionPayment stays separate since that's Ratel's own
-- platform revenue, a different concern from a business's own customer payments.
CREATE TABLE payment_transactions (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id       UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    direction         VARCHAR(10) NOT NULL,   -- INCOMING / OUTGOING
    source_type       VARCHAR(30) NOT NULL,   -- SERVICE_ORDER / SALE / BOOKING / PURCHASE_ORDER
    source_id         UUID,
    gateway           VARCHAR(20) NOT NULL,   -- PAYSTACK / MANUAL
    method            VARCHAR(20),            -- CARD / MOBILE_MONEY / CASH / BANK_TRANSFER
    amount            NUMERIC(12,2) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'GHS',
    status            VARCHAR(20) NOT NULL,   -- PENDING / SUCCESS / FAILED
    gateway_reference VARCHAR(100),
    customer_id       UUID,
    customer_phone    VARCHAR(20),
    note              TEXT,
    created_by        UUID,
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_transactions_business_id ON payment_transactions(business_id);
CREATE INDEX idx_payment_transactions_source ON payment_transactions(source_type, source_id);

ALTER TABLE sales ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PAID';
ALTER TABLE sales ADD COLUMN paystack_reference VARCHAR(100) UNIQUE;
UPDATE sales SET payment_status = 'PAID'; -- every existing sale was already point-of-sale-collected

ALTER TABLE purchase_orders ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID';
