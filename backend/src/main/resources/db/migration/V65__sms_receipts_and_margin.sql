-- V65: SMS receipts + Super Admin margin tracking.

-- Per business: PAPER (the default — nothing changes for anyone until they opt
-- in), SMS (text receipt only) or BOTH.
ALTER TABLE business_sms_accounts ADD COLUMN receipt_mode VARCHAR(10) NOT NULL DEFAULT 'PAPER';

-- What a message was sent about (a sale, for a receipt). A business can only ever
-- have ONE live receipt text per sale — the unique index is what makes "send the
-- receipt exactly once" hold even if two paths try at the same instant. A FAILED
-- receipt doesn't count, so it can be retried.
ALTER TABLE sms_messages ADD COLUMN source_type VARCHAR(20);
ALTER TABLE sms_messages ADD COLUMN source_id UUID;
CREATE UNIQUE INDEX uq_sms_receipt_per_source ON sms_messages(source_id)
    WHERE kind = 'RECEIPT' AND status <> 'FAILED';

-- Single-row platform settings: what the SMS provider (mNotify) charges Tallia
-- per credit, so the Super Admin page can show profit on each bundle.
CREATE TABLE sms_platform_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    provider_cost_per_credit NUMERIC(10, 4) CHECK (provider_cost_per_credit IS NULL OR provider_cost_per_credit >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO sms_platform_settings (id) VALUES (1);
