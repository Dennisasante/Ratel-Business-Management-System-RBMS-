-- V68: SMS campaigns (a promotional text to all customers, or a chosen few) + a per-customer opt-out.

-- A customer who has asked not to get promotional texts. Receipts are unaffected (they are not marketing).
ALTER TABLE customers ADD COLUMN sms_opt_out BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE sms_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name VARCHAR(80),
    message_template TEXT NOT NULL,
    audience_mode VARCHAR(10) NOT NULL,
    include_footer BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(12) NOT NULL DEFAULT 'SENDING',
    recipients_count INT NOT NULL,
    -- All the credits the campaign needs are taken up front, in one atomic step, so it can never
    -- start sending something the business can't pay for.
    credits_reserved INT NOT NULL,
    -- Credits handed back for texts that couldn't be sent (or were cancelled before sending).
    credits_refunded INT NOT NULL DEFAULT 0,
    -- Chosen by the browser when the confirm screen opens: pressing Send twice (or a retry after a
    -- dropped connection) can only ever create one campaign.
    request_key UUID NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    UNIQUE (business_id, request_key)
);

CREATE TABLE sms_campaign_recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES sms_campaigns(id) ON DELETE CASCADE,
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_id UUID,
    phone VARCHAR(20) NOT NULL,
    -- This customer's own text (the template with their name filled in) and what it costs.
    message TEXT NOT NULL,
    segments INT NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    provider_ref VARCHAR(100),
    error VARCHAR(300),
    claimed_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_campaign_recipients_campaign ON sms_campaign_recipients(campaign_id);
CREATE INDEX idx_campaign_recipients_pending ON sms_campaign_recipients(status) WHERE status IN ('PENDING', 'SENDING');
CREATE INDEX idx_campaigns_business ON sms_campaigns(business_id, created_at DESC);
