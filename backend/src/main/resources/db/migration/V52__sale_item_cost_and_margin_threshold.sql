-- Historical cost-of-goods snapshot per sale line (Dashboard/profitability
-- upgrade, Phase 1). Nullable — SERVICE lines have no cost concept at all
-- (see SaleItem.itemType), and existing PRODUCT rows predate this column.
--
-- For existing PRODUCT rows we backfill from each product's CURRENT
-- cost_price as the best available approximation (the exact cost at the
-- original sale's moment in time was never captured, so this is a one-time,
-- disclosed best-effort backfill — not fabricated data, the real current
-- cost of that real product). Every PRODUCT sale from this point forward
-- captures the true at-sale-time cost via SaleService.createSale().
ALTER TABLE sale_items ADD COLUMN unit_cost NUMERIC(12,2);

UPDATE sale_items si
SET unit_cost = p.cost_price
FROM products p
WHERE si.product_id = p.id
  AND si.item_type = 'PRODUCT'
  AND si.unit_cost IS NULL;

-- Configurable "below this margin, flag it" threshold per business (spec:
-- low-margin alerts) — a plain percentage, defaulting to 15%. Lives on
-- business_integrations alongside this codebase's other small per-business
-- numeric settings (booking_deposit_percent, cancellation_cutoff_hours),
-- rather than a new settings table.
ALTER TABLE business_integrations
    ADD COLUMN min_profit_margin_percent NUMERIC(5,2) NOT NULL DEFAULT 15.00;
