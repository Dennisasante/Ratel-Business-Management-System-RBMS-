-- A paid subscription that lapses now gets a 7-day grace window (billing_status
-- GRACE) before flipping to READ_ONLY, instead of losing access the instant
-- the deadline passes.
ALTER TABLE businesses ADD COLUMN grace_period_ends_at TIMESTAMPTZ;
