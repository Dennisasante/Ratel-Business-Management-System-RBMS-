-- A gift line always nets to GH₵0.00 (SaleService forces discount = full
-- line price when this is set, ignoring any client-sent discount) — kept
-- distinct from an ordinary manual discount so it can be reported on
-- separately ("Gifts given" vs "Discounts given") instead of blending in.
ALTER TABLE sale_items ADD COLUMN is_gift BOOLEAN NOT NULL DEFAULT FALSE;
