-- Lets a single Sale ring up a product and a service together (e.g. "pay
-- for the install, add a bottle of oil") in one transaction/receipt, instead
-- of two separate checkouts.

ALTER TABLE sale_items
    ADD COLUMN item_type VARCHAR(10) NOT NULL DEFAULT 'PRODUCT',
    ADD COLUMN service_catalog_id UUID REFERENCES service_catalog(id) ON DELETE SET NULL;

ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_item_type CHECK (item_type IN ('PRODUCT', 'SERVICE'));

-- Mutual exclusivity only — NOT "exactly one tied to item_type", since
-- product_id already goes null via ON DELETE SET NULL when a product is
-- deleted while old sale rows survive.
ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_one_reference CHECK (product_id IS NULL OR service_catalog_id IS NULL);

CREATE INDEX idx_sale_items_service_catalog_id ON sale_items(service_catalog_id);
