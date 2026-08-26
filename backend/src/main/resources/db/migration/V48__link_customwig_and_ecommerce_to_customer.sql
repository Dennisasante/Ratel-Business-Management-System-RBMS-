-- Custom Wig Requests and E-commerce Orders stored their own disconnected
-- customer_name/email/phone snapshots — unlike Sales/Service Orders/Bookings,
-- they never linked to the customers table, so the same person ordering
-- through both channels wasn't recognized as one customer. Ties them in via
-- customer_id, same ON DELETE SET NULL pattern as sales.customer_id.
ALTER TABLE custom_wig_requests ADD COLUMN customer_id UUID REFERENCES customers(id) ON DELETE SET NULL;
ALTER TABLE ecommerce_orders ADD COLUMN customer_id UUID REFERENCES customers(id) ON DELETE SET NULL;

CREATE INDEX idx_custom_wig_requests_customer_id ON custom_wig_requests (customer_id);
CREATE INDEX idx_ecommerce_orders_customer_id ON ecommerce_orders (customer_id);

-- Backfill: link existing rows to an already-existing Customer in the same
-- business whose normalized phone matches. Rows with no match are left
-- unlinked rather than fabricating a new Customer from a historical
-- snapshot — new submissions from now on create/link one at write time
-- (see CustomWigRequestService/WooCommerceSyncService).
UPDATE custom_wig_requests r
SET customer_id = c.id
FROM customers c
WHERE r.customer_id IS NULL
  AND c.business_id = r.business_id
  AND c.phone_normalized = CASE
      WHEN regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g') ~ '^00233[0-9]{9}$'
          THEN '0' || substring(regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g') from 6)
      WHEN regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g') ~ '^233[0-9]{9}$'
          THEN '0' || substring(regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g') from 4)
      WHEN regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g') ~ '^0[0-9]{9}$'
          THEN regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g')
      WHEN regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g') ~ '^[0-9]{9}$'
          THEN '0' || regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g')
      ELSE NULLIF(regexp_replace(r.customer_whatsapp, '[^0-9]', '', 'g'), '')
      END;

UPDATE ecommerce_orders o
SET customer_id = c.id
FROM customers c
WHERE o.customer_id IS NULL
  AND c.business_id = o.business_id
  AND c.phone_normalized = CASE
      WHEN regexp_replace(o.customer_phone, '[^0-9]', '', 'g') ~ '^00233[0-9]{9}$'
          THEN '0' || substring(regexp_replace(o.customer_phone, '[^0-9]', '', 'g') from 6)
      WHEN regexp_replace(o.customer_phone, '[^0-9]', '', 'g') ~ '^233[0-9]{9}$'
          THEN '0' || substring(regexp_replace(o.customer_phone, '[^0-9]', '', 'g') from 4)
      WHEN regexp_replace(o.customer_phone, '[^0-9]', '', 'g') ~ '^0[0-9]{9}$'
          THEN regexp_replace(o.customer_phone, '[^0-9]', '', 'g')
      WHEN regexp_replace(o.customer_phone, '[^0-9]', '', 'g') ~ '^[0-9]{9}$'
          THEN '0' || regexp_replace(o.customer_phone, '[^0-9]', '', 'g')
      ELSE NULLIF(regexp_replace(o.customer_phone, '[^0-9]', '', 'g'), '')
      END;
