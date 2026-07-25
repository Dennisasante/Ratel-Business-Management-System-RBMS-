-- V6: Staff account management, password recovery, and Super Admin audit trail.

-- Set true whenever an Owner/Manager creates a staff account with a temporary
-- password, or a Super Admin resets someone's password for support. Cleared
-- the moment the person successfully changes their own password.
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE platform_admin_reset_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id        UUID NOT NULL REFERENCES platform_admins(id) ON DELETE CASCADE,
    token           VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Records what the Super Admin does TO the platform (suspending/deleting a
-- business, resetting someone's password) — separate from activity_logs,
-- which records what business users do. Deliberately has no FK cascade to
-- businesses: if a business is deleted, the record that it WAS deleted must
-- survive that deletion, so business_id here is a plain column plus a name
-- snapshot rather than a live foreign key.
CREATE TABLE platform_audit_logs (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    admin_id                UUID REFERENCES platform_admins(id) ON DELETE SET NULL,
    action                  VARCHAR(500) NOT NULL,
    business_id             UUID,
    business_name_snapshot  VARCHAR(150),
    target_user_id          UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX idx_platform_admin_reset_tokens_token ON platform_admin_reset_tokens(token);
CREATE INDEX idx_platform_audit_logs_created_at ON platform_audit_logs(created_at);
