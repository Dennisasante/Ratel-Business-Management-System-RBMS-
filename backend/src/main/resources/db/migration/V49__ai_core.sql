-- Tallia AI Phase 1 core tables. Additive only — no existing table is
-- touched. Every table is tenant-scoped via business_id, same cascade/index
-- conventions as the rest of the schema. The AI module itself is gated
-- through the existing enabledModules toggle (see PlatformBusinessService),
-- not through anything in this migration — a business gets none of this
-- until a Super Admin explicitly turns "AI" on for them.

-- One configuration row per business. business_id is unique so there's at
-- most one settings row ever, matching business_integrations' own
-- one-row-per-business shape (same unique-column pattern, not a synthetic
-- singleton flag).
CREATE TABLE ai_settings (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id            UUID NOT NULL UNIQUE REFERENCES businesses(id) ON DELETE CASCADE,
    active                 BOOLEAN NOT NULL DEFAULT true,
    agent_name             VARCHAR(80) NOT NULL DEFAULT 'Tallia',
    greeting               TEXT,
    tone                   VARCHAR(50),
    system_instructions    TEXT,
    human_handoff_enabled  BOOLEAN NOT NULL DEFAULT true,
    human_handoff_message  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Business-specific knowledge the AI is allowed to draw on when answering —
-- deliberately plain varchar for category (see PhoneUtils/status-column
-- conventions elsewhere in this schema), not a native Postgres enum, so new
-- categories (HOTEL, RESTAURANT, ...) never need a migration to add.
CREATE TABLE ai_knowledge_entries (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id  UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    content      TEXT NOT NULL,
    category     VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_knowledge_entries_business_id ON ai_knowledge_entries (business_id);
-- Retrieval always filters "this business's active entries" — composite
-- index matches that exact query shape, same reasoning as
-- idx_customers_business_phone_normalized.
CREATE INDEX idx_ai_knowledge_entries_business_active ON ai_knowledge_entries (business_id, is_active);

-- One row per customer conversation thread, whichever channel it came in
-- on. customer_id is nullable — a conversation can start before the
-- customer is identified/resolved. assigned_user_id is a plain nullable
-- UUID (not a hard FK to users) to match how assignedStaffId is handled
-- elsewhere: it's tracking-only, not a referential constraint the AI path
-- needs to enforce.
CREATE TABLE ai_conversations (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id      UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    customer_id      UUID REFERENCES customers(id) ON DELETE SET NULL,
    channel          VARCHAR(20) NOT NULL DEFAULT 'WEB_DEMO',
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    assigned_user_id UUID,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_conversations_business_id ON ai_conversations (business_id);
CREATE INDEX idx_ai_conversations_customer_id ON ai_conversations (customer_id);

-- No FK from ai_messages to ai_conversations' business_id — messages simply
-- carry their own business_id copy (denormalized, matching how e.g.
-- payment_transactions carries its own business_id rather than joining
-- through source_id) so a message row can always be tenant-checked directly
-- without a join, same reasoning as everywhere else in this schema.
CREATE TABLE ai_messages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id     UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_messages_business_id ON ai_messages (business_id);
CREATE INDEX idx_ai_messages_conversation_id ON ai_messages (conversation_id);
CREATE INDEX idx_ai_messages_created_at ON ai_messages (created_at);

-- Dedicated AI audit trail, deliberately separate from activity_logs (see
-- Phase 0 reconnaissance §7) — activity_logs still gets a human-readable
-- line for every AI mutation via the existing ActivityLogService, this
-- table is the structured, tool-call-level detail activity_logs' schema
-- can't hold (arguments/result JSON, approval state, per-call status).
-- conversation_id/message_id are nullable so a future non-conversational
-- AI trigger still has somewhere to log to.
CREATE TABLE ai_actions (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id           UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    conversation_id       UUID REFERENCES ai_conversations(id) ON DELETE SET NULL,
    message_id            UUID REFERENCES ai_messages(id) ON DELETE SET NULL,
    tool_name             VARCHAR(60) NOT NULL,
    arguments_json        TEXT,
    result_json           TEXT,
    status                VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    requires_approval     BOOLEAN NOT NULL DEFAULT false,
    approved              BOOLEAN,
    resulting_entity_type VARCHAR(50),
    resulting_entity_id   UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_actions_business_id ON ai_actions (business_id);
CREATE INDEX idx_ai_actions_conversation_id ON ai_actions (conversation_id);
