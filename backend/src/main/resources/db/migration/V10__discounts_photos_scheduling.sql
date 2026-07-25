-- V10: discretionary discounts (per sale line item, and a tracked field on
-- service orders), photo attachments on service orders, and optional
-- appointment scheduling for service orders.

ALTER TABLE sale_items ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

-- service_orders.price stays the final charged amount (unchanged everywhere it's
-- already used for revenue); discount_amount is purely a transparency/reporting
-- field recording how much was knocked off from the catalog/base price to get there.
ALTER TABLE service_orders ADD COLUMN discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

-- Nullable: most orders are still walk-in/same-day: this only gets set when a
-- client books ahead for a future slot.
ALTER TABLE service_orders ADD COLUMN scheduled_at TIMESTAMPTZ;
CREATE INDEX idx_service_orders_scheduled_at ON service_orders(business_id, scheduled_at);

CREATE TABLE service_order_photos (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id       UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    service_order_id  UUID NOT NULL REFERENCES service_orders(id) ON DELETE CASCADE,
    url               TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_service_order_photos_order_id ON service_order_photos(service_order_id);
