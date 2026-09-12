package com.ratel.rbms.dto;

import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.entity.enums.ChannelConnectionState;

import java.time.Instant;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the business-facing channel connection detail (richer
 * than the existing read-only {@link AiChannelStatusResponse}, which remains unchanged and still
 * backs the simple list view). Safe metadata only — never the credential, never any ciphertext,
 * matching {@code WhatsAppBindingResponse}'s own established discipline exactly. Deliberately
 * channel-generic field names ({@code externalAccountId}/{@code externalSenderId}, not
 * {@code phoneNumberId}/{@code whatsappBusinessAccountId}) so this same response shape serves any
 * future channel without modification.
 *
 * <p>Hardening §2: {@code connectionState} on the wire is ALWAYS the caller-supplied
 * {@code effectiveState} — an explicit parameter, never read off {@code binding.getConnectionState()}
 * directly — so it is structurally impossible for a response to ever show a value this class
 * itself pulled from a possibly-stale persisted column. See
 * {@code AiChannelConnectionService.deriveState}, the one function every caller uses to compute it
 * fresh before building this response.
 */
public record AiChannelConnectionResponse(
        String channel,
        String connectionState,
        String connectionMethod,
        boolean active,
        boolean configured,
        String externalAccountId,
        String externalSenderId,
        String displayName,
        Instant lastVerifiedAt,
        Instant lastFailureAt,
        Instant updatedAt
) {
    public static AiChannelConnectionResponse from(AiChannel channel, AiChannelBinding binding, ChannelConnectionState effectiveState) {
        if (binding == null) {
            return new AiChannelConnectionResponse(
                    channel.name(), effectiveState.name(), null, false, false,
                    null, null, null, null, null, null);
        }
        return new AiChannelConnectionResponse(
                channel.name(),
                effectiveState.name(),
                binding.getConnectionMethod().name(),
                binding.isActive(),
                binding.getCredentialsEncrypted() != null && !binding.getCredentialsEncrypted().isBlank(),
                binding.getExternalAccountId(),
                binding.getExternalSenderId(),
                binding.getDisplayName(),
                binding.getLastVerifiedAt(),
                binding.getLastFailureAt(),
                binding.getUpdatedAt()
        );
    }
}
