package com.ratel.rbms.service;

import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;

import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the provider-neutral connection seam (frozen design,
 * "Phase 5D — Final Architecture Review Before Implementation" §2/§4). Everything about HOW a
 * channel's credentials are acquired lives behind this interface — {@code AiChannelConnectionService}
 * (the business-facing orchestrator) never knows whether an implementation pastes a token manually
 * or redirects through a provider's own OAuth flow. This lives in the CONNECTOR/channel
 * infrastructure area of the {@code service} package, alongside {@link AiChannelAdapter} — it is
 * deliberately a SEPARATE interface from {@code AiChannelAdapter}: that interface is about
 * runtime MESSAGE normalization/delivery once a channel is already connected; this one is about
 * ESTABLISHING the connection in the first place. Neither interface's implementations ever call
 * into the other's concerns.
 *
 * <p>A {@code MANUAL} implementation (the only one Stage 0 builds — {@link WhatsAppManualConnectionInitiator})
 * has no real redirect step: {@link #beginConnection} always returns
 * {@link ConnectionInitiationResult#immediate()} and the caller proceeds straight to
 * {@link #completeConnection} with the same data. A future {@code PROVIDER_AUTH} implementation
 * would return a real redirect URL from {@link #beginConnection} and only populate the binding
 * later, from {@link #completeConnection} once an OAuth callback arrives — the business-facing
 * service and every downstream consumer of {@link AiChannelBinding} need zero changes either way.
 */
public interface ChannelConnectionInitiator {

    AiChannel channel();

    /** Starts a connection attempt. MANUAL implementations return {@link ConnectionInitiationResult#immediate()}. */
    ConnectionInitiationResult beginConnection(UUID businessId, ConnectionParams params);

    /**
     * Builds an in-memory PREVIEW of what {@link #completeConnection} would produce — never
     * persisted, never returned by any repository query, safe to mutate freely. Lets
     * {@code AiChannelConnectionService} validate a proposed credential change against the real
     * provider BEFORE committing it, so a failed rotation attempt can never destroy an already
     *-working connection (hardening §4) — the generic service never needs to know this channel's
     * own field names to build that preview; only this initiator does.
     */
    AiChannelBinding buildCandidate(UUID businessId, AiChannelBinding existing, ConnectionParams params);

    /** Creates or updates this business's {@link AiChannelBinding} for this channel. Never runs a health check itself — see {@link #testHealth}. */
    AiChannelBinding completeConnection(UUID businessId, ConnectionCallbackData data);

    /** Validates the binding's current credentials against the real provider — never sends a customer-facing message. */
    ConnectionHealthResult testHealth(AiChannelBinding binding);
}
