-- Staff no longer get real accounts/login — just a name to attribute work to.
-- Existing STAFF-role users are converted in place: staff_members reuses the
-- same id as the source users row, so assigned_staff_id (repointed below)
-- needs no data rewriting at all. Their user row is then deactivated so they
-- can no longer log in (AuthService.completeLogin() already gates on
-- is_active), but stays around for historical actor fields (created_by, etc).
CREATE TABLE staff_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(50),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO staff_members (id, business_id, full_name, active, created_at)
SELECT id, business_id, full_name, is_active, created_at FROM users WHERE role = 'STAFF';

ALTER TABLE service_orders DROP CONSTRAINT service_orders_assigned_staff_id_fkey;
ALTER TABLE service_orders ADD CONSTRAINT service_orders_assigned_staff_id_fkey
    FOREIGN KEY (assigned_staff_id) REFERENCES staff_members(id) ON DELETE SET NULL;

UPDATE users SET is_active = false WHERE role = 'STAFF';
