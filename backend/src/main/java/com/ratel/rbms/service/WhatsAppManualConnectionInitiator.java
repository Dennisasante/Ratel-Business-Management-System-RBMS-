package com.ratel.rbms.service;

import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.entity.enums.ChannelConnectionMethod;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the {@code MANUAL} {@link ChannelConnectionInitiator}
 * for WhatsApp: an admin pastes provider-issued identifiers/credentials they obtained themselves
 * from Meta's own console, exactly the same field shape {@code WhatsAppBindingService} (the
 * existing Super-Admin-only surface) has always accepted.
 *
 * <p>Expected keys in {@link ConnectionParams}/{@link ConnectionCallbackData}: {@code phoneNumberId}
 * (required to connect; the WhatsApp Cloud API phone-number identifier), {@code accessToken}
 * (required only when no binding exists yet — omitted on an update leaves the stored token
 * untouched, exactly matching {@code WhatsAppBindingUpdateRequest}'s existing semantics),
 * {@code whatsappBusinessAccountId} (optional), {@code displayName} (optional).
 *
 * <p>Hardening §4 (credential rotation safety): {@link #buildCandidate} and
 * {@link #completeConnection} share one merge function, but {@link #buildCandidate} always
 * operates on a freshly-built, DETACHED {@link AiChannelBinding} copy — never the actual
 * JPA-managed instance — so {@code AiChannelConnectionService} can safely mutate/validate a
 * preview without any risk of Hibernate's dirty-checking flushing an unvalidated change.
 */
@Component
public class WhatsAppManualConnectionInitiator implements ChannelConnectionInitiator {

    private final AiChannelBindingRepository aiChannelBindingRepository;
    private final WhatsAppApiClient whatsAppApiClient;

    public WhatsAppManualConnectionInitiator(AiChannelBindingRepository aiChannelBindingRepository,
                                              WhatsAppApiClient whatsAppApiClient) {
        this.aiChannelBindingRepository = aiChannelBindingRepository;
        this.whatsAppApiClient = whatsAppApiClient;
    }

    @Override
    public AiChannel channel() {
        return AiChannel.WHATSAPP;
    }

    // MANUAL has no redirect step — the caller proceeds straight to completeConnection with the
    // same data (see ChannelConnectionInitiator's own class comment).
    @Override
    public ConnectionInitiationResult beginConnection(UUID businessId, ConnectionParams params) {
        return ConnectionInitiationResult.immediate();
    }

    @Override
    public AiChannelBinding buildCandidate(UUID businessId, AiChannelBinding existing, ConnectionParams params) {
        return mergeIntoBinding(businessId, detachedCopyOrNull(existing), params.values());
    }

    @Override
    public AiChannelBinding completeConnection(UUID businessId, ConnectionCallbackData data) {
        AiChannelBinding existing = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).orElse(null);
        AiChannelBinding binding = mergeIntoBinding(businessId, existing, data.values());

        try {
            return aiChannelBindingRepository.saveAndFlush(binding);
        } catch (DataIntegrityViolationException e) {
            // The (channel, external_account_id) global-uniqueness guard from V50 — this exact
            // Phone Number ID is already connected to a DIFFERENT business. Never silently
            // reassign it; the admin must resolve that ambiguity themselves.
            throw new ApiException(HttpStatus.CONFLICT, "This Phone Number ID is already connected to another business.");
        }
    }

    @Override
    public ConnectionHealthResult testHealth(AiChannelBinding binding) {
        if (binding.getCredentialsEncrypted() == null || binding.getCredentialsEncrypted().isBlank()) {
            return ConnectionHealthResult.unhealthy("No access token configured yet.");
        }
        WhatsAppApiClient.PhoneNumberMetadata result = whatsAppApiClient.validatePhoneNumber(
                binding.getExternalAccountId(), binding.getCredentialsEncrypted());
        return result.valid()
                ? ConnectionHealthResult.healthy(result.verifiedName())
                : ConnectionHealthResult.unhealthy(result.errorMessage());
    }

    // Shared by buildCandidate (given a detached copy, never persisted) and completeConnection
    // (given the real managed-or-new entity, persisted by the caller) — the exact same field
    // application logic either way, so a validated candidate and what actually gets saved can
    // never diverge in shape.
    private AiChannelBinding mergeIntoBinding(UUID businessId, AiChannelBinding existingOrCopy, Map<String, String> values) {
        String phoneNumberId = values.get("phoneNumberId");
        String accessToken = values.get("accessToken");
        String whatsappBusinessAccountId = values.get("whatsappBusinessAccountId");
        String displayName = values.get("displayName");

        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Phone Number ID is required.");
        }

        AiChannelBinding binding = existingOrCopy != null
                ? existingOrCopy
                : AiChannelBinding.builder().businessId(businessId).channel(AiChannel.WHATSAPP).build();

        boolean isNewBinding = binding.getId() == null;
        if (isNewBinding && (accessToken == null || accessToken.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Access token is required to connect WhatsApp.");
        }

        binding.setExternalAccountId(phoneNumberId);
        if (whatsappBusinessAccountId != null) {
            binding.setExternalSenderId(whatsappBusinessAccountId);
        }
        if (displayName != null) {
            binding.setDisplayName(displayName);
        }
        // Write-only rotation, same semantics as WhatsAppBindingUpdateRequest — omitted/blank
        // leaves whatever is already stored untouched, it is never cleared implicitly.
        if (accessToken != null && !accessToken.isBlank()) {
            binding.setCredentialsEncrypted(accessToken);
        }
        binding.setConnectionMethod(ChannelConnectionMethod.MANUAL);
        // Connecting/reconnecting always (re)enables customer traffic — a deliberate deactivation
        // is a separate, explicit action (AiChannelConnectionService.setActive), never implied here.
        binding.setActive(true);
        return binding;
    }

    // A plain, unmanaged POJO with the same field VALUES as the managed entity — sharing its id
    // does not register it with the persistence context; it can never be flushed unless something
    // explicitly calls save()/merge() on it, which buildCandidate's caller never does.
    private AiChannelBinding detachedCopyOrNull(AiChannelBinding source) {
        if (source == null) {
            return null;
        }
        return AiChannelBinding.builder()
                .id(source.getId())
                .businessId(source.getBusinessId())
                .channel(source.getChannel())
                .externalAccountId(source.getExternalAccountId())
                .externalSenderId(source.getExternalSenderId())
                .displayName(source.getDisplayName())
                .credentialsEncrypted(source.getCredentialsEncrypted())
                .active(source.isActive())
                .connectionMethod(source.getConnectionMethod())
                .lastVerifiedAt(source.getLastVerifiedAt())
                .lastFailureAt(source.getLastFailureAt())
                .connectionState(source.getConnectionState())
                .build();
    }
}
