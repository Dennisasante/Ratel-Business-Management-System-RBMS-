-- Null means "hasn't seen the first-login walkthrough yet" — the frontend
-- shows the tour once and calls back to set this, same not-yet-vs-done shape
-- as SubscriptionPayment.paidAt elsewhere in this schema.
ALTER TABLE users ADD COLUMN onboarding_completed_at TIMESTAMPTZ;

-- Owner/staff -> platform support channel. Deliberately separate from
-- ActivityLog (an audit trail, not a conversation) and from CustomWigRequest
-- (a customer-facing quote flow) — this is the business asking Ratel itself
-- for help. requester_name/email are snapshotted at submission time so a
-- later staff deactivation/rename doesn't rewrite request history.
CREATE TABLE help_requests (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id       UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id           UUID REFERENCES users(id) ON DELETE SET NULL,
    requester_name    VARCHAR(150) NOT NULL,
    requester_email   VARCHAR(150) NOT NULL,
    category          VARCHAR(20) NOT NULL DEFAULT 'GENERAL', -- GENERAL / BUG / BILLING / FEATURE_REQUEST
    subject           VARCHAR(200) NOT NULL,
    message           TEXT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',    -- OPEN / RESOLVED
    admin_response    TEXT,
    responded_by      UUID REFERENCES platform_admins(id) ON DELETE SET NULL,
    responded_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_help_requests_business_id ON help_requests(business_id);
CREATE INDEX idx_help_requests_status ON help_requests(status);
