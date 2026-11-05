-- V66: Loyalty points. A "point" is a visit: at most one per customer per day, counted
-- when a qualifying sale is paid or a service order is completed. When a customer has
-- enough visits they are due a reward, which staff apply and mark redeemed.

-- Owner-chosen: does this service count as a visit? On by default; a business can
-- switch off the ones that shouldn't (a quick top-up, say).
ALTER TABLE service_catalog ADD COLUMN counts_for_loyalty BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE loyalty_programs (
    business_id UUID PRIMARY KEY REFERENCES businesses(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    visits_for_reward INT NOT NULL DEFAULT 10 CHECK (visits_for_reward >= 1),
    reward_description VARCHAR(120) NOT NULL DEFAULT 'a free service',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per customer once they have a first visit. The token is the unguessable
-- part of their personal points-page link. visits_total only ever goes up, and
-- visits_redeemed only ever goes up (each redemption consumes the threshold at the
-- time), so progress = visits_total - visits_redeemed can never be corrupted by a
-- later change to the program's target.
CREATE TABLE loyalty_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    token VARCHAR(16) NOT NULL UNIQUE,
    visits_total INT NOT NULL DEFAULT 0,
    visits_redeemed INT NOT NULL DEFAULT 0 CHECK (visits_redeemed <= visits_total),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (business_id, customer_id)
);

-- The unique constraint IS the "one visit per customer per day" rule: the second
-- insert for the same day simply doesn't happen, however many sales or paths race.
CREATE TABLE loyalty_visits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    visit_date DATE NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    source_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (business_id, customer_id, visit_date)
);

CREATE TABLE loyalty_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    visits_consumed INT NOT NULL CHECK (visits_consumed >= 1),
    reward_description VARCHAR(120) NOT NULL,
    redeemed_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_loyalty_customers_business ON loyalty_customers(business_id);
CREATE INDEX idx_loyalty_redemptions_customer ON loyalty_redemptions(business_id, customer_id, created_at DESC);
