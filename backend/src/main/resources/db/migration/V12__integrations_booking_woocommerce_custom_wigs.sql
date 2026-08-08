-- V12: WordPress/WooCommerce integration, booking widget, and custom wig
-- requests. See ecommerce-booking-integration-spec.md in the repo root.

-- One settings row per business — Paystack keys here are the CLIENT's own
-- account (their customers pay them directly), completely separate from the
-- platform's own PAYSTACK_SECRET_KEY/PUBLIC_KEY used for RBMS subscription
-- billing. Secret-ish columns are encrypted at rest by the entity layer
-- (EncryptedStringConverter), not by the database itself.
CREATE TABLE business_integrations (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id                 UUID NOT NULL UNIQUE REFERENCES businesses(id) ON DELETE CASCADE,
    paystack_public_key         TEXT,
    paystack_secret_key         TEXT,
    woocommerce_site_url        TEXT,
    woocommerce_consumer_key    TEXT,
    woocommerce_consumer_secret TEXT,
    woocommerce_webhook_secret  TEXT,
    whatsapp_notify_number      VARCHAR(20),
    test_mode                   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE service_catalog ADD COLUMN bookable_online BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id         UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    booking_number      BIGSERIAL,
    service_order_id    UUID NOT NULL REFERENCES service_orders(id) ON DELETE CASCADE,
    customer_name       VARCHAR(150) NOT NULL,
    customer_email      VARCHAR(255) NOT NULL,
    customer_whatsapp   VARCHAR(20) NOT NULL,
    manage_token        VARCHAR(64) NOT NULL UNIQUE,
    payment_status      VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    paystack_reference  VARCHAR(100) UNIQUE,
    is_test             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bookings_business_id ON bookings(business_id);
CREATE INDEX idx_bookings_service_order_id ON bookings(service_order_id);

-- E-commerce orders coming in from WooCommerce. Not reusing `sales` — a Sale
-- is an instant, already-complete POS transaction with no status pipeline;
-- a web order needs Received -> Processing -> Ready tracking.
CREATE TABLE ecommerce_orders (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id          UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    woo_order_id         BIGINT NOT NULL,
    order_number         VARCHAR(50) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    customer_name        VARCHAR(150),
    customer_email       VARCHAR(255),
    customer_phone       VARCHAR(20),
    total_amount         NUMERIC(12,2) NOT NULL,
    currency             VARCHAR(10) NOT NULL DEFAULT 'GHS',
    raw_payload          TEXT,  -- full webhook body as raw JSON text, debug/support only, never queried
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ecommerce_orders_business_woo UNIQUE (business_id, woo_order_id)
);

CREATE TABLE ecommerce_order_items (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ecommerce_order_id    UUID NOT NULL REFERENCES ecommerce_orders(id) ON DELETE CASCADE,
    product_name          VARCHAR(200) NOT NULL,
    product_id            UUID REFERENCES products(id) ON DELETE SET NULL,
    quantity              INT NOT NULL,
    unit_price            NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_ecommerce_orders_business_id ON ecommerce_orders(business_id);
CREATE INDEX idx_ecommerce_order_items_order_id ON ecommerce_order_items(ecommerce_order_id);

ALTER TABLE products ADD COLUMN woo_product_id BIGINT;
ALTER TABLE products ADD COLUMN publish_to_website BOOLEAN NOT NULL DEFAULT FALSE;

-- Custom wig configurator: business-defined attributes (Length, Color,
-- Texture...) each with priced options. Requests snapshot the attribute/option
-- labels and price modifiers at submission time (in `selections`), same
-- reasoning as sale_items snapshotting product_name — editing a price rule
-- later must never rewrite a customer's existing quote history.
CREATE TABLE custom_item_attributes (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    sort_order   INT NOT NULL DEFAULT 0
);

CREATE TABLE custom_item_attribute_options (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attribute_id    UUID NOT NULL REFERENCES custom_item_attributes(id) ON DELETE CASCADE,
    label           VARCHAR(100) NOT NULL,
    price_modifier  NUMERIC(12,2) NOT NULL DEFAULT 0,
    sort_order      INT NOT NULL DEFAULT 0
);

CREATE TABLE custom_wig_requests (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id            UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    request_number         BIGSERIAL,
    customer_name          VARCHAR(150) NOT NULL,
    customer_email         VARCHAR(255) NOT NULL,
    customer_whatsapp      VARCHAR(20) NOT NULL,
    selections             TEXT NOT NULL,  -- JSON array, serialized/parsed explicitly via Jackson in the service layer
    estimated_price        NUMERIC(12,2) NOT NULL,
    inspiration_photo_url  TEXT,
    notes                  TEXT,
    status                 VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    final_price            NUMERIC(12,2),
    owner_message          TEXT,
    is_test                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_custom_item_attributes_business_id ON custom_item_attributes(business_id);
CREATE INDEX idx_custom_item_attribute_options_attribute_id ON custom_item_attribute_options(attribute_id);
CREATE INDEX idx_custom_wig_requests_business_id ON custom_wig_requests(business_id);

-- Plan-based feature gating. Feature codes used by the backend gate:
-- BOOKING_WIDGET, WOOCOMMERCE_SYNC, CUSTOM_WIG_REQUESTS
ALTER TABLE subscription_plans ADD COLUMN features TEXT[] NOT NULL DEFAULT '{}';
