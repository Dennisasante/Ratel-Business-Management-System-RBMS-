-- Adds a normalized-phone column for duplicate detection across
-- "0244123456" / "+233244123456" / "233 244 123 456" / "024-412-3456"
-- forms of the same Ghana number. Logic here MUST stay in sync by hand
-- with PhoneUtils.normalize() in the Java codebase.
ALTER TABLE customers ADD COLUMN phone_normalized VARCHAR(20);

UPDATE customers
SET phone_normalized = CASE
    WHEN regexp_replace(phone, '[^0-9]', '', 'g') ~ '^00233[0-9]{9}$'
        THEN '0' || substring(regexp_replace(phone, '[^0-9]', '', 'g') from 6)
    WHEN regexp_replace(phone, '[^0-9]', '', 'g') ~ '^233[0-9]{9}$'
        THEN '0' || substring(regexp_replace(phone, '[^0-9]', '', 'g') from 4)
    WHEN regexp_replace(phone, '[^0-9]', '', 'g') ~ '^0[0-9]{9}$'
        THEN regexp_replace(phone, '[^0-9]', '', 'g')
    WHEN regexp_replace(phone, '[^0-9]', '', 'g') ~ '^[0-9]{9}$'
        THEN '0' || regexp_replace(phone, '[^0-9]', '', 'g')
    ELSE NULLIF(regexp_replace(phone, '[^0-9]', '', 'g'), '')
END
WHERE phone IS NOT NULL;

CREATE INDEX idx_customers_business_phone_normalized ON customers (business_id, phone_normalized);
