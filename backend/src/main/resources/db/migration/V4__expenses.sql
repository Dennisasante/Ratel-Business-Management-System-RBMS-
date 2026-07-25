-- V4: Expenses — the other half of the revenue/cost picture Financial Reports needs.
-- Same tenant pattern as everything else: business_id on the table, scoped queries only.

CREATE TABLE expenses (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    category        VARCHAR(30)  NOT NULL, -- RENT, UTILITIES, TRANSPORT, SUPPLIES, SALARY, MARKETING, OTHER
    description     VARCHAR(255),
    amount          NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    expense_date    DATE NOT NULL,
    recorded_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_expenses_business_id ON expenses(business_id);
CREATE INDEX idx_expenses_business_date ON expenses(business_id, expense_date);
