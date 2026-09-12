package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiChannelConnectionResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.entity.enums.ChannelConnectionState;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the business-facing (OWNER/MANAGER) channel
 * connection surface (frozen design, "Phase 5D — Final Architecture Review Before Implementation"
 * §2/§3/§4/§9, hardened per the Stage 0 acceptance gate §2/§4/§5). This is the ONE orchestrator
 * every channel's connect/test/activate flow goes through — generic over {@link AiChannel},
 * resolving the right {@link ChannelConnectionInitiator} per channel exactly the way
 * {@link AiChannelDeliveryService} already resolves the right {@link AiChannelAdapter} per
 * channel. Stage 0 registers exactly one initiator ({@link WhatsAppManualConnectionInitiator});
 * Instagram/Voice/Website Chat/a future WhatsApp OAuth path all become new
 * {@link ChannelConnectionInitiator} beans, never a change here.
 *
 * <p><b>connectionState invariant (hardening §2)</b>: the persisted column is a CACHE, never
 * trusted directly by any read path. Every response is built from {@link #deriveState}, computed
 * fresh from the three raw signals, every time — {@link #getDetail} additionally self-heals the
 * persisted column if it's ever found to disagree with the freshly-derived value, so drift can
 * never silently persist. The same {@link #deriveState} function is shared with the existing
 * Super-Admin {@code WhatsAppBindingService}, so the same underlying {@link AiChannelBinding} row
 * can never show an inconsistent state depending on which surface last touched it.
 *
 * <p><b>Credential rotation safety (hardening §4)</b>: {@link #connect} never overwrites an
 * already-{@code CONNECTED} binding's working credentials with a replacement that fails
 * validation — see the candidate-then-validate-then-commit sequence there.
 */
@Service
public class AiChannelConnectionService {

    private final AiChannelBindingRepository aiChannelBindingRepository;
    private final Map<AiChannel, ChannelConnectionInitiator> initiatorsByChannel;
    private final ActivityLogService activityLogService;
    private final ModuleAccessService moduleAccessService;

    public AiChannelConnectionService(
            AiChannelBindingRepository aiChannelBindingRepository,
            List<ChannelConnectionInitiator> initiators,
            ActivityLogService activityLogService,
            ModuleAccessService moduleAccessService
    ) {
        this.aiChannelBindingRepository = aiChannelBindingRepository;
        this.initiatorsByChannel = initiators.stream()
                .collect(java.util.stream.Collectors.toMap(ChannelConnectionInitiator::channel, i -> i));
        this.activityLogService = activityLogService;
        this.moduleAccessService = moduleAccessService;
    }

    /**
     * The single derivation function for {@code connectionState}, shared with
     * {@code WhatsAppBindingService} so the value can never drift depending on which surface
     * (business-facing or Super-Admin) last wrote the row. Pure: no side effects, no I/O, computed
     * from exactly the three signals the architecture review names as authoritative. Every caller
     * in this class — including a decision as security-relevant as "is this connection currently
     * trustworthy enough to protect from a bad rotation" — calls this fresh rather than trusting
     * {@code binding.getConnectionState()} directly (hardening §2/§4).
     */
    static ChannelConnectionState deriveState(AiChannelBinding binding) {
        if (binding.getCredentialsEncrypted() == null || binding.getCredentialsEncrypted().isBlank()) {
            return ChannelConnectionState.NOT_CONNECTED;
        }
        if (!binding.isActive()) {
            return ChannelConnectionState.DISCONNECTED;
        }
        boolean lastSignalWasFailure = binding.getLastFailureAt() != null
                && (binding.getLastVerifiedAt() == null || binding.getLastFailureAt().isAfter(binding.getLastVerifiedAt()));
        return lastSignalWasFailure ? ChannelConnectionState.DEGRADED : ChannelConnectionState.CONNECTED;
    }

    /**
     * Read path. Never trusts the persisted {@code connectionState} column as authoritative — it
     * is recomputed fresh from the binding's raw signals every call, and the persisted cache is
     * opportunistically self-healed (in the same transaction) if it's ever found to disagree.
     */
    @Transactional
    public AiChannelConnectionResponse getDetail(AiChannel channel) {
        UUID businessId = requireAiModule();
        AiChannelBinding binding = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, channel).orElse(null);
        if (binding == null) {
            return AiChannelConnectionResponse.from(channel, null, ChannelConnectionState.NOT_CONNECTED);
        }

        ChannelConnectionState effective = deriveState(binding);
        if (effective != binding.getConnectionState()) {
            // The persisted cache disagreed with the raw signals — self-heal it. This should be
            // rare (only a bug elsewhere, or a manual DB edit, could cause it); correcting it here
            // means drift can never silently persist, and the wire response is unaffected either
            // way since it always uses `effective`, not the stored value.
            binding.setConnectionState(effective);
            binding = aiChannelBindingRepository.save(binding);
        }
        return AiChannelConnectionResponse.from(channel, binding, effective);
    }

    /**
     * Connects (no existing binding) or reconnects/updates credentials (a binding already exists)
     * for this business's chosen channel.
     *
     * <p>Hardening §4 — exact sequence: build an in-memory, never-persisted PREVIEW of what the
     * new credentials would produce, validate that preview against the real provider, and ONLY
     * THEN decide whether to commit:
     * <ul>
     *   <li>No existing binding, or the existing one isn't currently CONNECTED (nothing working
     *       to protect) — commit regardless of validation outcome, exactly like a first-time
     *       connection always has (lets an admin iterate on a still-broken configuration).</li>
     *   <li>An existing binding IS currently CONNECTED and the new credentials FAIL validation —
     *       reject outright, commit nothing, the working connection is completely untouched.</li>
     * </ul>
     */
    @Transactional
    public AiChannelConnectionResponse connect(AiChannel channel, ConnectionParams params) {
        UUID businessId = requireAiModule();
        ChannelConnectionInitiator initiator = requireInitiator(channel);

        ConnectionInitiationResult initiation = initiator.beginConnection(businessId, params);
        if (initiation.requiresRedirect()) {
            // Stage 0 never reaches this branch — every registered initiator today is MANUAL.
            // Kept as a real, working seam for a future PROVIDER_AUTH initiator, not dead code.
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                    "Provider-authorized connection isn't available yet for this channel.");
        }

        AiChannelBinding existing = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, channel).orElse(null);
        AiChannelBinding candidate = initiator.buildCandidate(businessId, existing, params);
        ConnectionHealthResult health = initiator.testHealth(candidate);

        boolean protectingAWorkingConnection = existing != null && deriveState(existing) == ChannelConnectionState.CONNECTED;
        if (protectingAWorkingConnection && !health.healthy()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Couldn't validate the new credentials"
                            + (health.message() != null ? " (" + health.message() + ")" : "")
                            + " — the previous, working " + label(channel) + " connection remains active and unchanged.");
        }

        AiChannelBinding binding = initiator.completeConnection(businessId, new ConnectionCallbackData(params.values()));
        if (health.healthy()) {
            binding.setLastVerifiedAt(Instant.now());
        } else {
            binding.setLastFailureAt(Instant.now());
        }
        ChannelConnectionState effective = deriveState(binding);
        binding.setConnectionState(effective);
        binding = aiChannelBindingRepository.save(binding);

        activityLogService.log("Connected " + label(channel) + " for this business", "AI_CHANNEL_BINDING", binding.getId());
        return AiChannelConnectionResponse.from(channel, binding, effective);
    }

    /** Re-validates an already-connected channel's credentials against the real provider. */
    @Transactional
    public AiChannelConnectionResponse test(AiChannel channel) {
        UUID businessId = requireAiModule();
        AiChannelBinding binding = requireBinding(businessId, channel);
        ChannelConnectionInitiator initiator = requireInitiator(channel);

        ChannelConnectionState effective = applyHealthCheck(binding, initiator);
        return AiChannelConnectionResponse.from(channel, binding, effective);
    }

    /**
     * Activates or deactivates customer traffic for an already-configured channel. This is also
     * the "disconnect" action (spec §3/§12.3 — "Disconnect = active=false, no hard-delete
     * endpoint") — deactivating never implies a health problem, and reactivating never implies the
     * credentials changed (that's {@link #connect}'s job).
     */
    @Transactional
    public AiChannelConnectionResponse setActive(AiChannel channel, boolean active) {
        UUID businessId = requireAiModule();
        AiChannelBinding binding = requireBinding(businessId, channel);

        binding.setActive(active);
        ChannelConnectionState effective = deriveState(binding);
        binding.setConnectionState(effective);
        binding = aiChannelBindingRepository.save(binding);

        activityLogService.log((active ? "Activated " : "Disconnected ") + label(channel) + " for this business",
                "AI_CHANNEL_BINDING", binding.getId());
        return AiChannelConnectionResponse.from(channel, binding, effective);
    }

    // The one place lastVerifiedAt/lastFailureAt/connectionState are ever written together for a
    // pure health re-check — guarantees they can never drift out of sync with each other or with
    // what a real health check actually found. Returns the freshly-derived state so the caller
    // never has to re-read binding.getConnectionState() either.
    private ChannelConnectionState applyHealthCheck(AiChannelBinding binding, ChannelConnectionInitiator initiator) {
        ConnectionHealthResult result = initiator.testHealth(binding);
        if (result.healthy()) {
            binding.setLastVerifiedAt(Instant.now());
        } else {
            binding.setLastFailureAt(Instant.now());
        }
        ChannelConnectionState effective = deriveState(binding);
        binding.setConnectionState(effective);
        aiChannelBindingRepository.save(binding);
        return effective;
    }

    private UUID requireAiModule() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");
        return businessId;
    }

    private ChannelConnectionInitiator requireInitiator(AiChannel channel) {
        ChannelConnectionInitiator initiator = initiatorsByChannel.get(channel);
        if (initiator == null) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "This channel isn't available to connect yet.");
        }
        return initiator;
    }

    private AiChannelBinding requireBinding(UUID businessId, AiChannel channel) {
        return aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, channel)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No " + label(channel) + " connection configured for this business."));
    }

    private String label(AiChannel channel) {
        return switch (channel) {
            case WEB_DEMO -> "Web Demo";
            case WHATSAPP -> "WhatsApp";
            case INSTAGRAM -> "Instagram";
            case FACEBOOK -> "Facebook Messenger";
            case PHONE -> "Phone";
            case SMS -> "SMS";
            case EMAIL -> "Email";
        };
    }
}
