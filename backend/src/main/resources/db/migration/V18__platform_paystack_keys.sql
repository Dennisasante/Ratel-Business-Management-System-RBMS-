-- RBMS's own Paystack keys (subscription billing), configurable from the
-- Super Admin UI instead of only via the PAYSTACK_SECRET_KEY/PAYSTACK_PUBLIC_KEY
-- env vars set at deploy time. Secret is encrypted at rest, same converter as
-- BusinessIntegrations.paystack_secret_key.
ALTER TABLE platform_billing_settings ADD COLUMN paystack_secret_key VARCHAR;
ALTER TABLE platform_billing_settings ADD COLUMN paystack_public_key VARCHAR;
