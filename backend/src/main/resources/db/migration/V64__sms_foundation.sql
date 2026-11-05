-- SMS foundation: per-business credit wallets, platform-defined credit bundles,
-- a Paystack purchase record, an append-only ledger, and a message log.
--
-- Tallia holds ONE mNotify account (key in env, never in the DB); a business
-- never sees it. A business buys credits from Tallia (priced above mNotify's own
-- rate — that margin is the Super Admin's call, set per bundle) and spends them
-- here; every send is also drawn from Tallia's mNotify balance behind the scenes.

CREATE TABLE sms_bundles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) NOT NULL,
    credits INT NOT NULL CHECK (credits > 0),
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per business, created lazily the first time the SMS module is touched.
-- sender_id is the name recipients see (3-11 alphanumeric chars, mNotify limit);
-- the Super Admin sets it and flips sender_status once mNotify has approved it.
-- credit_balance can never go negative (CHECK) — the debit query is also
-- conditional, so two concurrent sends can't both spend the last credit.
CREATE TABLE business_sms_accounts (
    business_id UUID PRIMARY KEY REFERENCES businesses(id) ON DELETE CASCADE,
    sender_id VARCHAR(11),
    sender_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    credit_balance INT NOT NULL DEFAULT 0 CHECK (credit_balance >= 0),
    low_balance_threshold INT NOT NULL DEFAULT 20,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sms_credit_purchases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    bundle_id UUID REFERENCES sms_bundles(id) ON DELETE SET NULL,
    -- Snapshots: editing or retiring a bundle later never rewrites what a past
    -- purchase was for.
    bundle_name VARCHAR(80) NOT NULL,
    credits INT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'GHS',
    paystack_reference VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sms_credit_purchases_business ON sms_credit_purchases(business_id, created_at DESC);
CREATE INDEX idx_sms_credit_purchases_status ON sms_credit_purchases(status, created_at);

-- Append-only: every change to credit_balance has exactly one row here, so the
-- balance can always be reconciled against its history.
CREATE TABLE sms_credit_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    delta INT NOT NULL,
    balance_after INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    reference VARCHAR(100),
    description TEXT,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sms_credit_ledger_business ON sms_credit_ledger(business_id, created_at DESC);

CREATE TABLE sms_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_id UUID,
    recipient VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    sender_id VARCHAR(11) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    segments INT NOT NULL,
    credits_used INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_ref VARCHAR(100),
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sms_messages_business ON sms_messages(business_id, created_at DESC);
CREATE INDEX idx_sms_messages_status ON sms_messages(status, created_at);
