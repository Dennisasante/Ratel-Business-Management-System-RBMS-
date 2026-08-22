-- V45: Tax/shipping on invoices, plus a Tax ID and an optional signature
-- image at the business level (shown on every invoice, same idea as the
-- existing logo).

ALTER TABLE businesses ADD COLUMN tax_id VARCHAR(50);
ALTER TABLE businesses ADD COLUMN signature_url VARCHAR(500);

-- tax_rate is nullable (no tax charged at all is the common case for a
-- small business); tax_amount/shipping_amount are computed-and-stored the
-- same way subtotal/discount_amount/total_amount already are, so a PDF
-- generated later never has to re-derive them from a rate that might have
-- since changed on the business.
ALTER TABLE invoices ADD COLUMN tax_rate NUMERIC(5,2);
ALTER TABLE invoices ADD COLUMN tax_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN shipping_amount NUMERIC(12,2) NOT NULL DEFAULT 0;
