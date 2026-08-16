-- In-app notification inbox for Owner/Manager — a shared per-business feed
-- (not per-user) so both roles see the same list and unread count, same
-- tradeoff payment_transactions/help_requests already make.
CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    type         VARCHAR(30) NOT NULL, -- NEW_BOOKING, NEW_CUSTOM_WIG_REQUEST
    title        VARCHAR(200) NOT NULL,
    body         VARCHAR(500),
    source_type  VARCHAR(30), -- BOOKING, CUSTOM_WIG_REQUEST
    source_id    UUID,
    is_read      BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_business_unread ON notifications(business_id, is_read);
CREATE INDEX idx_notifications_business_created ON notifications(business_id, created_at DESC);
