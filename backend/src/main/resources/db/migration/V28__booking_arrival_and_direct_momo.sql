-- Null means the customer hasn't been marked as physically arrived for this
-- appointment yet. Deliberately separate from the underlying ServiceOrder's
-- own status pipeline (RECEIVED/IN_PROGRESS/...) — that tracks work progress,
-- this tracks whether the person showed up, which staff need to record
-- independently (a booking can sit at RECEIVED for hours before anyone arrives).
ALTER TABLE bookings ADD COLUMN arrived_at TIMESTAMPTZ;
