-- How an expense was actually paid — CASH or MOBILE_MONEY. Default backfills
-- every historical row to CASH (the previous implicit assumption), matching
-- the no-DB-CHECK-constraint convention already used by expenses.category
-- and sales.payment_method.
ALTER TABLE expenses ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'CASH';
