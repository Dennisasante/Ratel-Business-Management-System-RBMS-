ALTER TABLE business_integrations
    ADD COLUMN booking_payment_policy VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN booking_deposit_percent INTEGER NOT NULL DEFAULT 50;
