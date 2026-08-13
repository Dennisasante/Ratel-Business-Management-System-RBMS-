-- Staff logging a request that arrived through an informal channel
-- (Instagram DM, WhatsApp, a phone call) may not have an email on hand —
-- matches Booking.customerEmail/customerWhatsapp's existing nullable
-- precedent for the same "staff entered this on the customer's behalf" case.
ALTER TABLE custom_wig_requests ALTER COLUMN customer_email DROP NOT NULL;
ALTER TABLE custom_wig_requests ALTER COLUMN customer_whatsapp DROP NOT NULL;

-- Free text ("Instagram DM", "Walk-in", "Phone call", ...) rather than an
-- enum — null for requests that came through the public widget, where the
-- channel is implicitly "website" and not worth asking the customer to state.
ALTER TABLE custom_wig_requests ADD COLUMN source VARCHAR(60);
