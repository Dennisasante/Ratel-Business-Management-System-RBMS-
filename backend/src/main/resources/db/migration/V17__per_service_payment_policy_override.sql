-- NULL = use the business's default booking_payment_policy (today's behavior).
-- Set to 'NONE'/'DEPOSIT'/'FULL' to override it for just this service/package,
-- e.g. a business that requires deposits generally but sells a walk-in-only
-- add-on that should never ask for payment online.
ALTER TABLE service_catalog ADD COLUMN payment_policy_override VARCHAR(20);
ALTER TABLE service_packages ADD COLUMN payment_policy_override VARCHAR(20);
