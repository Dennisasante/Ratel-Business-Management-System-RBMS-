-- Tallia AI Phase 3A — channel abstraction foundation. Additive only: no
-- existing table or column is modified, every new column is nullable, and
-- WEB_DEMO's existing rows/behavior are entirely untouched. This migration
-- only builds the schema a future WhatsApp/Instagram/Facebook/Phone/SMS/
-- Email adapter would plug into — it does not connect to any of them.

-- One row per external channel identity a business has connected (a
-- WhatsApp phone-number-ID, an Instagram/Facebook page, ...). Deliberately
-- GLOBAL uniqueness on (channel, external_account_id), not scoped by
-- business_id: an external identifier like a WhatsApp phone-number-ID is
-- unique across the whole external platform, not per-tenant, so two
-- different RBMS businesses must never be able to register the same one —
-- that ambiguity has to be rejected outright, not guessed at by application
-- code. business_id is an attribute OF the binding, not part of what makes
-- it unique.
--
-- credentials_encrypted reuses the exact same AES-256-GCM column convention
-- BusinessIntegrations already uses for Paystack/WooCommerce secrets (see
-- EncryptedStringConverter) — no second encryption scheme. Nothing writes a
-- real value into it yet; Phase 3A creates no external credentials at all.
CREATE TABLE ai_channel_bindings (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id           UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    channel               VARCHAR(20) NOT NULL,
    external_account_id   VARCHAR(200),
    external_sender_id    VARCHAR(200),
    display_name          VARCHAR(200),
    credentials_encrypted TEXT,
    is_active             BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_channel_bindings_business_id ON ai_channel_bindings (business_id);

-- The uniqueness rule from the comment above, DB-enforced: a duplicate
-- external identity for a channel is rejected, never silently allowed to
-- route to whichever business happened to register it first or second.
CREATE UNIQUE INDEX uq_ai_channel_bindings_channel_external_account
    ON ai_channel_bindings (channel, external_account_id)
    WHERE external_account_id IS NOT NULL;

-- Conversation identity — nullable/additive. A WEB_DEMO conversation leaves
-- all three null and behaves exactly as it did in Phase 1/2.
ALTER TABLE ai_conversations
    ADD COLUMN external_conversation_id VARCHAR(200),
    ADD COLUMN external_user_id         VARCHAR(200),
    ADD COLUMN channel_binding_id       UUID REFERENCES ai_channel_bindings(id) ON DELETE SET NULL;

-- The same external conversation ID on the same binding must always resolve
-- back to the same AI conversation — DB-enforced, not just application logic.
CREATE UNIQUE INDEX uq_ai_conversations_binding_external_conversation
    ON ai_conversations (channel_binding_id, external_conversation_id)
    WHERE channel_binding_id IS NOT NULL AND external_conversation_id IS NOT NULL;

-- Message-level idempotency — the mandatory "the same external message is
-- never processed twice" requirement. channel_binding_id is denormalized
-- directly onto the message row (matching this table's own existing
-- business_id denormalization, see V49) so the idempotency lookup never
-- needs a join through ai_conversations.
ALTER TABLE ai_messages
    ADD COLUMN channel_binding_id  UUID REFERENCES ai_channel_bindings(id) ON DELETE SET NULL,
    ADD COLUMN external_message_id VARCHAR(200);

CREATE UNIQUE INDEX uq_ai_messages_binding_external_message
    ON ai_messages (channel_binding_id, external_message_id)
    WHERE channel_binding_id IS NOT NULL AND external_message_id IS NOT NULL;

-- Audit traceability only (§18/§19 of the spec) — nullable, no new
-- constraints. Lets a mutation be traced end-to-end: external message ->
-- AI response -> tool call -> resulting booking, on whichever channel it
-- came in on.
ALTER TABLE ai_actions
    ADD COLUMN channel             VARCHAR(20),
    ADD COLUMN channel_binding_id  UUID REFERENCES ai_channel_bindings(id) ON DELETE SET NULL,
    ADD COLUMN external_message_id VARCHAR(200);
