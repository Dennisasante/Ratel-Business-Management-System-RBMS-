-- Scheduling robustness on the catalog item itself.
ALTER TABLE service_catalog ADD COLUMN duration_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE service_catalog ADD COLUMN max_concurrent_bookings INT NOT NULL DEFAULT 1;

-- Business-wide working hours — one window, all days configurable on/off.
ALTER TABLE business_integrations ADD COLUMN working_days INT[] NOT NULL DEFAULT '{1,2,3,4,5,6}'; -- ISO weekday, 1=Mon..7=Sun; default excludes Sunday
ALTER TABLE business_integrations ADD COLUMN working_hours_start TIME NOT NULL DEFAULT '09:00';
ALTER TABLE business_integrations ADD COLUMN working_hours_end TIME NOT NULL DEFAULT '18:00';

CREATE TABLE business_blackout_dates (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    date         DATE NOT NULL,
    label        VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_blackout_dates UNIQUE (business_id, date)
);
CREATE INDEX idx_business_blackout_dates_business_id ON business_blackout_dates(business_id);

-- Hosted booking page for businesses without their own website.
ALTER TABLE businesses ADD COLUMN slug VARCHAR(80) UNIQUE;

-- Backfill a slug for every existing business from its name, deduping collisions
-- with a numeric suffix. New businesses get one generated the same way at creation.
DO $$
DECLARE
    biz RECORD;
    base_slug VARCHAR(80);
    candidate VARCHAR(80);
    suffix INT;
BEGIN
    FOR biz IN SELECT id, name FROM businesses ORDER BY created_at LOOP
        base_slug := lower(regexp_replace(trim(biz.name), '[^a-zA-Z0-9]+', '-', 'g'));
        base_slug := trim(both '-' from base_slug);
        IF base_slug = '' THEN
            base_slug := 'business';
        END IF;
        candidate := base_slug;
        suffix := 2;
        WHILE EXISTS (SELECT 1 FROM businesses WHERE slug = candidate) LOOP
            candidate := base_slug || '-' || suffix;
            suffix := suffix + 1;
        END LOOP;
        UPDATE businesses SET slug = candidate WHERE id = biz.id;
    END LOOP;
END $$;

ALTER TABLE businesses ALTER COLUMN slug SET NOT NULL;
