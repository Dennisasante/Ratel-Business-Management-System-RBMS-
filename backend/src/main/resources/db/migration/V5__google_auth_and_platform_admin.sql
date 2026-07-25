-- V5: Google sign-in support + platform-level Super Admin.
--
-- Super Admin is deliberately NOT a row in `users` — that table is
-- business-scoped (business_id NOT NULL, FK cascade-deletes with the
-- business). A platform owner isn't part of any business, so it gets its
-- own table and its own auth flow entirely (see PlatformAdminService /
-- PlatformAuthController on the backend).

-- Google sign-in: a user created via Google has no password, so password_hash
-- has to become nullable. auth_provider tracks how the account was created,
-- mainly for display/audit purposes — login-by-Google works by email match
-- regardless of provider, since Google's verified email is trustworthy proof
-- of ownership either way.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

CREATE TABLE platform_admins (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email           VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
