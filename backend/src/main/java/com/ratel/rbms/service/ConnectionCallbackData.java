package com.ratel.rbms.service;

import java.util.Map;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the generic input to
 * {@link ChannelConnectionInitiator#completeConnection}. For a {@code MANUAL} connection method
 * (Stage 0's only implementation) this carries the same field values {@link ConnectionParams} did,
 * unchanged — there is no redirect round-trip, so "begin" and "complete" happen back-to-back with
 * identical data. A future {@code PROVIDER_AUTH} initiator would instead populate this from an
 * OAuth callback (an authorization code, a state token) after exchanging it for real credentials
 * itself — {@code AiChannelConnectionService} never needs to know the difference.
 */
public record ConnectionCallbackData(Map<String, String> values) {

    public String get(String key) {
        return values == null ? null : values.get(key);
    }
}
