CREATE TABLE push_subscriptions (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id    UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint       TEXT NOT NULL,
    p256dh         TEXT NOT NULL,
    auth           TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_business ON push_subscriptions(business_id);
