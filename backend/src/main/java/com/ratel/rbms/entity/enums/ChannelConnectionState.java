package com.ratel.rbms.entity.enums;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the derived/cached connection-health state of one
 * {@link com.ratel.rbms.entity.AiChannelBinding} (frozen design, "Phase 5D — Final Architecture
 * Review Before Implementation" §3). This is NEVER an independent source of truth — it is always
 * recomputed from exactly three underlying signals (credential existence, {@code active}, and the
 * {@code lastVerifiedAt}/{@code lastFailureAt} pair) by
 * {@code AiChannelConnectionService}/{@code WhatsAppBindingService}, the only two writers, both of
 * which recompute and persist it in the same transaction as any change to those signals. No other
 * code may set this field directly.
 *
 * <p>{@link #CONNECTING} is modeled for lifecycle completeness (a future provider-authorization
 * redirect window) — Stage 0's manual-only connection path never produces it; a manual form
 * submission moves straight from {@link #NOT_CONNECTED} to {@link #CONNECTED}/{@link #DEGRADED} in
 * one step.
 *
 * <p>{@link #REAUTH_REQUIRED} is likewise modeled but structurally unreachable in Stage 0: today's
 * only health signal ({@code WhatsAppApiClient.validatePhoneNumber}'s boolean success/failure) has
 * no way to distinguish an auth-specific failure (expired/revoked token) from a generic one
 * (transient provider error) — every health-check failure surfaces as {@link #DEGRADED} until a
 * later stage adds provider-error classification. This mirrors this codebase's own established
 * "kept for forward compatibility, currently unreachable" convention (e.g.
 * {@code PackagePricingService}'s {@code SUBSTITUTION_LIMIT_EXCEEDED}).
 */
public enum ChannelConnectionState {
    /** No credential has ever been configured. */
    NOT_CONNECTED,
    /** A provider-authorization handshake is in progress (transient — Stage 0 never persists this). */
    CONNECTING,
    /** Credential exists, channel is active, and the most recent health signal succeeded. */
    CONNECTED,
    /** Credential exists, channel is active, but the most recent health signal failed. */
    DEGRADED,
    /** Reserved — not reachable by any Stage 0 code path (see class comment). */
    REAUTH_REQUIRED,
    /** Explicitly deactivated by an admin — never implies a health problem. */
    DISCONNECTED
}
