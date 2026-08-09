-- Pay-in-person as an explicit, owner-togglable customer choice.
ALTER TABLE business_integrations ADD COLUMN booking_allow_pay_in_person BOOLEAN NOT NULL DEFAULT FALSE;
-- bookings.payment_status gains a new value 'PAY_IN_PERSON' alongside UNPAID/PAID/FAILED — plain VARCHAR, no schema change.

-- Cancellation/reschedule cutoff (0 = no restriction).
ALTER TABLE business_integrations ADD COLUMN booking_cancellation_cutoff_hours INT NOT NULL DEFAULT 0;

-- Home-service location capture.
ALTER TABLE service_catalog ADD COLUMN requires_location BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN customer_location TEXT;

-- Structured packages: a package is a sibling of service_catalog (its own price/
-- duration/capacity/bookable-online), with a real linked list of component items.
CREATE TABLE service_packages (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id              UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    service_type_id          UUID NOT NULL REFERENCES service_types(id),
    name                     VARCHAR(150) NOT NULL,
    description              TEXT,
    price                    NUMERIC(12,2) NOT NULL DEFAULT 0,
    active                   BOOLEAN NOT NULL DEFAULT TRUE,
    bookable_online          BOOLEAN NOT NULL DEFAULT FALSE,
    duration_minutes         INT NOT NULL DEFAULT 60,
    max_concurrent_bookings  INT NOT NULL DEFAULT 1,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_service_packages_business_id ON service_packages(business_id);

CREATE TABLE service_package_items (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    package_id          UUID NOT NULL REFERENCES service_packages(id) ON DELETE CASCADE,
    service_catalog_id  UUID NOT NULL REFERENCES service_catalog(id),
    quantity            INT NOT NULL DEFAULT 1
);
CREATE INDEX idx_service_package_items_package_id ON service_package_items(package_id);

-- A booking-originated order references exactly one of service_catalog_id (existing)
-- or service_package_id (new) — a package books as one atomic appointment, same as
-- a single service does today; its component items are descriptive, not separate orders.
ALTER TABLE service_orders ADD COLUMN service_package_id UUID REFERENCES service_packages(id) ON DELETE SET NULL;
