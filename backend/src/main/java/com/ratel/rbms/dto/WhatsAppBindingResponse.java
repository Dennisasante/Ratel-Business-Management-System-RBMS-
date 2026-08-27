package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiChannelBinding;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe metadata only (spec §7) — never the access token, never any
 * ciphertext, whether or not it's actually configured. "configured" is
 * exactly {@code credentialsEncrypted != null}, i.e. an access token has
 * been set at least once; it says nothing about whether that token still
 * works (see WhatsAppConnectionTestResponse for that).
 */
public record WhatsAppBindingResponse(
        UUID id,
        UUID businessId,
        String businessName,
        String whatsappBusinessAccountId,
        String phoneNumberId,
        String displayName,
        boolean active,
        boolean configured,
        Instant createdAt,
        Instant updatedAt
) {
    public static WhatsAppBindingResponse from(AiChannelBinding binding, String businessName) {
        return new WhatsAppBindingResponse(
                binding.getId(),
                binding.getBusinessId(),
                businessName,
                binding.getExternalSenderId(),
                binding.getExternalAccountId(),
                binding.getDisplayName(),
                binding.isActive(),
                binding.getCredentialsEncrypted() != null && !binding.getCredentialsEncrypted().isBlank(),
                binding.getCreatedAt(),
                binding.getUpdatedAt()
        );
    }
}
