-- How many months a subscription payment actually paid for (1/3/6/12) —
-- record-keeping alongside the existing period_start/period_end, which
-- remain the source of truth for how long the paid period actually spans.
-- Defaults to 1 for every existing row, matching the single-cycle behavior
-- that was the only option before this.
ALTER TABLE subscription_payments ADD COLUMN months INT NOT NULL DEFAULT 1;

-- The number of months a business most recently checked out for — read back
-- by BillingService.attemptAutoCharge() so a saved-card renewal repeats the
-- same cycle length (and discount tier) the business originally chose,
-- instead of collapsing every renewal to a single month. Defaults to 1 so
-- every existing business keeps renewing exactly as it does today until it
-- checks out again under the new multi-month flow.
ALTER TABLE businesses ADD COLUMN billing_cycle_months INT NOT NULL DEFAULT 1;
