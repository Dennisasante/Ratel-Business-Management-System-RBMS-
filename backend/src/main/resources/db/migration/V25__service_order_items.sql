-- Lets one service order hold multiple services (a client getting 2-3
-- different services in one visit, instead of one order per service).
-- service_orders keeps its existing scalar service_type_id/service_catalog_id/
-- price/discount_amount fields — they become "primary type" (first item's)
-- and running totals (sum of items), so every existing single-service order,
-- query, and filter keeps working exactly as before. service_order_items is
-- the new source of truth for "what services, individually."
CREATE TABLE service_order_items (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    service_order_id    UUID NOT NULL REFERENCES service_orders(id) ON DELETE CASCADE,
    service_type_id     UUID NOT NULL REFERENCES service_types(id),
    service_catalog_id  UUID REFERENCES service_catalog(id) ON DELETE SET NULL,
    service_name        VARCHAR(150) NOT NULL,      -- snapshot, same reasoning as sale_items.product_name
    price               NUMERIC(12,2) NOT NULL,
    discount_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_service_order_items_order_id ON service_order_items(service_order_id);
CREATE INDEX idx_service_order_items_business_id ON service_order_items(business_id);

-- Backfill: every existing order gets exactly one item mirroring its current
-- scalar fields, so the receipt/report/view can read from service_order_items
-- uniformly for old and new orders alike.
INSERT INTO service_order_items (business_id, service_order_id, service_type_id, service_catalog_id, service_name, price, discount_amount, created_at)
SELECT so.business_id, so.id, so.service_type_id, so.service_catalog_id,
       COALESCE(sc.name, sp.name, st.name, 'Service'), so.price, so.discount_amount, so.created_at
FROM service_orders so
LEFT JOIN service_catalog sc ON sc.id = so.service_catalog_id
LEFT JOIN service_packages sp ON sp.id = so.service_package_id
LEFT JOIN service_types st ON st.id = so.service_type_id;

-- Payment status + Paystack reference on the order itself — a staff-created
-- walk-in order has no Booking row to carry these on, unlike the public
-- booking-widget flow.
ALTER TABLE service_orders
    ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    ADD COLUMN paystack_reference VARCHAR(100) UNIQUE;
