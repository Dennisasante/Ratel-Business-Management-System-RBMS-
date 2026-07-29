-- V11: Subscription billing (Paystack). Replaces the do-nothing free-text
-- `subscription_plan` column on businesses with a real plan/payment/status model.

CREATE TABLE subscription_plans (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                 VARCHAR(50) NOT NULL,
    price                NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(10) NOT NULL DEFAULT 'GHS',
    billing_period_days  INT NOT NULL DEFAULT 30,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE, -- archived, not deleted, once a business has ever used it
    sort_order           INT NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Single-row global setting, managed by the super admin.
CREATE TABLE platform_billing_settings (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trial_days        INT NOT NULL DEFAULT 14,
    usd_display_rate  NUMERIC(10,4),                    -- GHS-per-USD, e.g. 15.20 — manually set; null hides the USD toggle entirely
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO platform_billing_settings (trial_days) VALUES (14);

ALTER TABLE businesses
    ADD COLUMN subscription_plan_id     UUID REFERENCES subscription_plans(id) ON DELETE SET NULL,
    ADD COLUMN billing_status           VARCHAR(20) NOT NULL DEFAULT 'TRIALING', -- TRIALING, ACTIVE, READ_ONLY
    ADD COLUMN trial_ends_at            TIMESTAMPTZ,
    ADD COLUMN current_period_ends_at   TIMESTAMPTZ,
    ADD COLUMN expiry_reminder_sent_at  TIMESTAMPTZ; -- last time the "renew soon" email went out, to avoid re-sending it daily

-- Grandfather existing (already-active) businesses so this ships without
-- instantly locking anyone currently using the system into read-only.
UPDATE businesses
SET billing_status = 'ACTIVE', current_period_ends_at = now() + INTERVAL '30 days'
WHERE is_active = TRUE;

CREATE TABLE subscription_payments (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id          UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    subscription_plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    amount               NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(10) NOT NULL,
    paystack_reference   VARCHAR(100) NOT NULL UNIQUE,  -- idempotency key: webhook + client verify can't double-record
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    period_start         TIMESTAMPTZ,
    period_end           TIMESTAMPTZ,
    paid_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_payments_business_id ON subscription_payments(business_id);
CREATE INDEX idx_businesses_billing_status ON businesses(billing_status);
-- old free-text `subscription_plan` column on businesses kept for one release, dropped later.
