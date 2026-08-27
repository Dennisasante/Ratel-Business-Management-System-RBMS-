package com.ratel.rbms.service;

import com.ratel.rbms.dto.WhatsAppBindingCreateRequest;
import com.ratel.rbms.dto.WhatsAppBindingResponse;
import com.ratel.rbms.dto.WhatsAppBindingUpdateRequest;
import com.ratel.rbms.dto.WhatsAppConnectionTestResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.repository.BusinessRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Super-Admin-only management of a business's WhatsApp {@link AiChannelBinding}
 * (spec §6/§7/§29) — Phase 3B is developer/admin-configured, not a Meta
 * OAuth onboarding flow. business_id always comes from the caller (a
 * Platform admin endpoint, never tenant-scoped by design — see
 * PlatformBusinessService's own doc comments for the same posture), never
 * from anywhere inside the request body. Access tokens are write-only:
 * once saved, nothing here (or anywhere else) ever reads it back out to an
 * API response — see WhatsAppBindingResponse.
 */
@Service
public class WhatsAppBindingService {

    private final AiChannelBindingRepository aiChannelBindingRepository;
    private final BusinessRepository businessRepository;
    private final PlatformAuditLogService platformAuditLogService;
    private final WhatsAppApiClient whatsAppApiClient;

    public WhatsAppBindingService(
            AiChannelBindingRepository aiChannelBindingRepository,
            BusinessRepository businessRepository,
            PlatformAuditLogService platformAuditLogService,
            WhatsAppApiClient whatsAppApiClient
    ) {
        this.aiChannelBindingRepository = aiChannelBindingRepository;
        this.businessRepository = businessRepository;
        this.platformAuditLogService = platformAuditLogService;
        this.whatsAppApiClient = whatsAppApiClient;
    }

    public WhatsAppBindingResponse get(UUID businessId) {
        Business business = requireBusiness(businessId);
        AiChannelBinding binding = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No WhatsApp binding configured for this business."));
        return WhatsAppBindingResponse.from(binding, business.getName());
    }

    @Transactional
    public WhatsAppBindingResponse create(UUID adminId, UUID businessId, WhatsAppBindingCreateRequest req) {
        Business business = requireBusiness(businessId);

        if (aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "This business already has a WhatsApp binding — update it instead.");
        }

        AiChannelBinding binding = AiChannelBinding.builder()
                .businessId(businessId)
                .channel(AiChannel.WHATSAPP)
                .externalAccountId(req.phoneNumberId())
                .externalSenderId(req.whatsappBusinessAccountId())
                .displayName(req.displayName())
                .credentialsEncrypted(req.accessToken())
                .active(req.active())
                .build();

        try {
            binding = aiChannelBindingRepository.saveAndFlush(binding);
        } catch (DataIntegrityViolationException e) {
            // The (channel, external_account_id) uniqueness guard from V50 —
            // this exact Phone Number ID is already connected to a DIFFERENT
            // business. Never silently reassign it; the admin must resolve
            // that ambiguity themselves.
            throw new ApiException(HttpStatus.CONFLICT, "This Phone Number ID is already connected to another business.");
        }

        platformAuditLogService.log(adminId, "Connected a WhatsApp number for \"" + business.getName() + "\"",
                businessId, business.getName(), null);
        return WhatsAppBindingResponse.from(binding, business.getName());
    }

    @Transactional
    public WhatsAppBindingResponse update(UUID adminId, UUID businessId, WhatsAppBindingUpdateRequest req) {
        Business business = requireBusiness(businessId);
        AiChannelBinding binding = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No WhatsApp binding configured for this business."));

        if (req.whatsappBusinessAccountId() != null) binding.setExternalSenderId(req.whatsappBusinessAccountId());
        if (req.phoneNumberId() != null && !req.phoneNumberId().isBlank()) binding.setExternalAccountId(req.phoneNumberId());
        if (req.displayName() != null) binding.setDisplayName(req.displayName());
        if (req.accessToken() != null && !req.accessToken().isBlank()) binding.setCredentialsEncrypted(req.accessToken());
        if (req.active() != null) binding.setActive(req.active());

        try {
            binding = aiChannelBindingRepository.saveAndFlush(binding);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This Phone Number ID is already connected to another business.");
        }

        platformAuditLogService.log(adminId, "Updated the WhatsApp binding for \"" + business.getName() + "\"",
                businessId, business.getName(), null);
        return WhatsAppBindingResponse.from(binding, business.getName());
    }

    @Transactional
    public WhatsAppBindingResponse setActive(UUID adminId, UUID businessId, boolean active) {
        Business business = requireBusiness(businessId);
        AiChannelBinding binding = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No WhatsApp binding configured for this business."));

        binding.setActive(active);
        binding = aiChannelBindingRepository.save(binding);

        platformAuditLogService.log(adminId, (active ? "Activated" : "Deactivated") + " the WhatsApp binding for \"" + business.getName() + "\"",
                businessId, business.getName(), null);
        return WhatsAppBindingResponse.from(binding, business.getName());
    }

    /** Validates the configured phone number/token against the real Graph API — never sends a customer-facing message (spec §31). */
    public WhatsAppConnectionTestResponse testConnection(UUID businessId) {
        AiChannelBinding binding = aiChannelBindingRepository.findByBusinessIdAndChannel(businessId, AiChannel.WHATSAPP)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No WhatsApp binding configured for this business."));

        if (binding.getCredentialsEncrypted() == null || binding.getCredentialsEncrypted().isBlank()) {
            return new WhatsAppConnectionTestResponse(false, null, null, "No access token configured yet.");
        }

        WhatsAppApiClient.PhoneNumberMetadata result = whatsAppApiClient.validatePhoneNumber(
                binding.getExternalAccountId(), binding.getCredentialsEncrypted());
        return new WhatsAppConnectionTestResponse(result.valid(), result.displayPhoneNumber(), result.verifiedName(), result.errorMessage());
    }

    private Business requireBusiness(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found."));
    }
}
