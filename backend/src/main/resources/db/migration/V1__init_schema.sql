-- V1: Core multi-tenant foundation: businesses + users
-- Every tenant-owned table going forward carries a business_id FK to enforce isolation.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE businesses (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(150)  NOT NULL,
    industry        VARCHAR(50)   NOT NULL, -- SALON, RETAIL, RESTAURANT, SCHOOL, OTHER
    logo_url        VARCHAR(500),
    location        VARCHAR(255),
    contact_email   VARCHAR(150),
    contact_phone   VARCHAR(50),
    currency        VARCHAR(10)   NOT NULL DEFAULT 'GHS',
    subscription_plan VARCHAR(30) NOT NULL DEFAULT 'FREE_TRIAL',
    enabled_modules TEXT[]        NOT NULL DEFAULT ARRAY['INVENTORY','SALES','CUSTOMERS','EXPENSES']::TEXT[],
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    full_name       VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(30)  NOT NULL DEFAULT 'OWNER', -- OWNER, MANAGER, SALES_PERSON, ACCOUNTANT
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_business_email UNIQUE (business_id, email)
);

-- Lightweight audit trail, per the "John created sale #1024" example in the spec.
CREATE TABLE activity_logs (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(255) NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_business_id ON users(business_id);
CREATE INDEX idx_activity_logs_business_id ON activity_logs(business_id);
