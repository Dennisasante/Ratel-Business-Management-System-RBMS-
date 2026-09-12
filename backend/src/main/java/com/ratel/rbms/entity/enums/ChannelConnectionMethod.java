package com.ratel.rbms.entity.enums;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — how an {@link com.ratel.rbms.entity.AiChannelBinding}'s
 * credentials were acquired. Purely descriptive/audit metadata — never branched on by
 * {@code AiChannelRouter}/{@code AiChannelAdapter}/{@code AiChatService}, which remain completely
 * indifferent to how a binding came to exist (frozen design principle: CORE/AI must never leak
 * provider-specific or acquisition-specific concepts).
 */
public enum ChannelConnectionMethod {
    /** An admin manually entered provider-issued identifiers/credentials (Stage 0's only implementation). */
    MANUAL,
    /** Acquired via a provider-hosted authorization flow (e.g. Meta Embedded Signup) — not implemented in Stage 0. */
    PROVIDER_AUTH
}
