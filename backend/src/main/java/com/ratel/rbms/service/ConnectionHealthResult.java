package com.ratel.rbms.service;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the outcome of
 * {@link ChannelConnectionInitiator#testHealth}. {@code message} is always safe to show a business
 * user (never a raw provider error payload, never a credential) — see each initiator's own mapping
 * from its provider's real error shape into this generic one.
 */
public record ConnectionHealthResult(boolean healthy, String message) {

    public static ConnectionHealthResult healthy(String message) {
        return new ConnectionHealthResult(true, message);
    }

    public static ConnectionHealthResult unhealthy(String message) {
        return new ConnectionHealthResult(false, message);
    }
}
