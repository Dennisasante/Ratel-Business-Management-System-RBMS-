package com.ratel.rbms.service;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the outcome of
 * {@link ChannelConnectionInitiator#beginConnection}. A {@code MANUAL} initiator (Stage 0's only
 * implementation) always returns {@link #immediate()} — there is nothing to redirect to; the
 * caller proceeds straight to {@code completeConnection} with the same data. A future
 * {@code PROVIDER_AUTH} initiator would instead return {@code requiresRedirect=true} with a real
 * {@code redirectUrl}, and {@code completeConnection} would only be called later, from the OAuth
 * callback — {@code AiChannelConnectionService} branches on {@code requiresRedirect} without ever
 * knowing which provider it's talking to.
 */
public record ConnectionInitiationResult(boolean requiresRedirect, String redirectUrl) {

    public static ConnectionInitiationResult immediate() {
        return new ConnectionInitiationResult(false, null);
    }
}
