-- Lets staff create a booking on a customer's behalf (e.g. a phone-in
-- request) instead of only accepting bookings made through the public
-- widget. A staff-entered booking may reference an existing Customer
-- record directly, and — since it can be typed in with only a name, no
-- contact details on hand yet — customer_email/customer_whatsapp can no
-- longer be mandatory the way the public widget always guarantees them.

ALTER TABLE bookings
    ADD COLUMN customer_id UUID REFERENCES customers(id) ON DELETE SET NULL,
    ALTER COLUMN customer_email DROP NOT NULL,
    ALTER COLUMN customer_whatsapp DROP NOT NULL;

CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);
