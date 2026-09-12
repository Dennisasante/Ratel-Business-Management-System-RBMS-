-- Talia Unified Platform — Phase 5D Stage 0: business-facing AI channel connection management +
-- the provider-neutral connection lifecycle (frozen design, "Phase 5D — Final Architecture Review
-- Before Implementation" §3/§9). Purely additive to ai_channel_bindings (V50) — no existing column
-- or constraint is altered, and no other table is touched. Phase 5C (V1-V59) is untouched.
--
-- connection_state is a DERIVED/CACHED value, never an independent source of truth: it is always
-- recomputed from exactly three underlying signals (credentials_encrypted existence, is_active,
-- and the last_verified_at/last_failure_at pair) by AiChannelConnectionService/WhatsAppBindingService
-- — the only two writers of this column, both of which recompute and persist it in the SAME
-- transaction as any change to those three signals. No other code path may set it directly.

ALTER TABLE ai_channel_bindings
    ADD COLUMN connection_method VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN last_verified_at TIMESTAMPTZ,
    ADD COLUMN last_failure_at TIMESTAMPTZ,
    ADD COLUMN connection_state VARCHAR(20) NOT NULL DEFAULT 'NOT_CONNECTED';

ALTER TABLE ai_channel_bindings
    ADD CONSTRAINT chk_ai_channel_bindings_connection_method
        CHECK (connection_method IN ('MANUAL', 'PROVIDER_AUTH')),
    ADD CONSTRAINT chk_ai_channel_bindings_connection_state
        CHECK (connection_state IN ('NOT_CONNECTED', 'CONNECTING', 'CONNECTED', 'DEGRADED', 'REAUTH_REQUIRED', 'DISCONNECTED'));

-- Backfill every existing binding (today, only ever created via the Super-Admin-only WhatsApp path,
-- always MANUAL). No historical health-check record exists for these rows, so a configured,
-- currently-active binding is optimistically treated as CONNECTED (matching AiChannelStatusService's
-- own existing "configured && active => Connected" read today) rather than left in a state that
-- would misrepresent a real, working production connection as never-verified.
UPDATE ai_channel_bindings
SET connection_state = CASE
    WHEN credentials_encrypted IS NULL OR credentials_encrypted = '' THEN 'NOT_CONNECTED'
    WHEN is_active = FALSE THEN 'DISCONNECTED'
    ELSE 'CONNECTED'
END;
