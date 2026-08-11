-- Lets a Super Admin charge a specific business something other than the
-- plan's list price (a negotiated rate, a grandfathered discount) without
-- needing a whole separate plan just for them. Null = charge the plan price.
ALTER TABLE businesses ADD COLUMN price_override NUMERIC(12,2);
