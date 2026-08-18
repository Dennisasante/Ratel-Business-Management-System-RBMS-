CREATE TABLE pending_approvals (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id    UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    source_type    VARCHAR(20) NOT NULL, -- SALE, SERVICE_ORDER, CUSTOM_WIG_REQUEST
    source_id      UUID NOT NULL,
    action_type    VARCHAR(20) NOT NULL, -- EDIT_PRICE, REFUND
    payload        TEXT NOT NULL,        -- JSON snapshot of the original request, replayed verbatim on approval
    summary        VARCHAR(300) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    requested_by   UUID NOT NULL REFERENCES users(id),
    requested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by     UUID REFERENCES users(id),
    decided_at     TIMESTAMPTZ,
    decision_note  VARCHAR(500)
);

CREATE INDEX idx_pending_approvals_business_status ON pending_approvals(business_id, status);
CREATE INDEX idx_pending_approvals_source ON pending_approvals(source_type, source_id);
