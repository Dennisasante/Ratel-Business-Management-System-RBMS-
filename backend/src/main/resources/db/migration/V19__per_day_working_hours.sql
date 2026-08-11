-- Replaces the single business-wide working_days + working_hours_start/end
-- pair with one row per open day, so e.g. Sunday can have different hours
-- than the rest of the week. A day is "open" iff it has a row here.
CREATE TABLE business_working_hours (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    day_of_week  INT NOT NULL, -- ISO weekday, 1=Mon..7=Sun
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    UNIQUE (business_id, day_of_week)
);
CREATE INDEX idx_business_working_hours_business_id ON business_working_hours(business_id);

-- Backfill: every existing business's open days get a row carrying its old
-- single start/end pair, before that data disappears with the column drop below.
INSERT INTO business_working_hours (business_id, day_of_week, start_time, end_time)
SELECT business_id, unnest(working_days), working_hours_start, working_hours_end
FROM business_integrations;

ALTER TABLE business_integrations DROP COLUMN working_days;
ALTER TABLE business_integrations DROP COLUMN working_hours_start;
ALTER TABLE business_integrations DROP COLUMN working_hours_end;
