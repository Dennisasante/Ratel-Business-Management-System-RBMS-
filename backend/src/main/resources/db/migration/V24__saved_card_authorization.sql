-- Lets a business save a card at checkout (Paystack returns a reusable
-- authorization_code on the verify response, no special init-time request
-- needed) so its subscription can auto-renew without a manual click each
-- period.
ALTER TABLE businesses
    ADD COLUMN paystack_authorization_code VARCHAR(100),
    ADD COLUMN card_last4 VARCHAR(4),
    ADD COLUMN card_brand VARCHAR(30),
    ADD COLUMN auto_renew_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE subscription_payments
    ADD COLUMN authorization_code VARCHAR(100),
    -- Whether the Owner checked "save this card for automatic renewal" at
    -- checkout — read back in BillingService.verifyPayment() once the
    -- payment succeeds, since checkout and verify are separate requests
    -- (verify can even arrive via the Paystack webhook with no request
    -- context at all).
    ADD COLUMN save_card_requested BOOLEAN NOT NULL DEFAULT FALSE;
