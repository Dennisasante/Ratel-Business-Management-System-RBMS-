-- Tracks how much of a sale/order has actually been collected, so partial
-- payments (PARTIALLY_PAID) and refunds can be recorded — previously
-- payment_status was the only signal, an all-or-nothing PAID/UNPAID/FAILED.
-- balance_due is deliberately not persisted — it's always (total - amount_paid),
-- derived at the response layer like every other computed total in this schema.
ALTER TABLE sales ADD COLUMN amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE service_orders ADD COLUMN amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0;

UPDATE sales SET amount_paid = total_amount WHERE payment_status = 'PAID';
UPDATE service_orders SET amount_paid = price WHERE payment_status = 'PAID';
