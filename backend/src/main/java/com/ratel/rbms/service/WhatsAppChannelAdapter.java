package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The WhatsApp Cloud API channel adapter — everything WhatsApp-shaped lives
 * here and nowhere else. Never queries a customer, creates a booking,
 * queries services, calls the LLM, or executes an AI tool (spec §11/§18) —
 * this class only ever turns a WhatsApp-shaped payload into an
 * {@link IncomingAiMessage}, and an {@link OutgoingAiMessage} into a
 * WhatsApp Cloud API request via {@link WhatsAppApiClient}.
 *
 * Inbound "raw payload" is a {@link WhatsAppInboundContext} — one message
 * object out of Meta's webhook envelope, plus the metadata WhatsAppWebhookService
 * already extracted (phoneNumberId/wabaId) — never the whole raw envelope,
 * and never anything AiChatService would need to know is WhatsApp-shaped.
 *
 * Outbound never receives an AiConversation, a repository, a Booking, a
 * Customer, or TenantContext (spec §18) — OutgoingAiMessage.metadata carries
 * only the two plain, non-sensitive routing strings this adapter needs
 * (CHANNEL_BINDING_ID_KEY / RECIPIENT_WA_ID_KEY); the adapter resolves its
 * own AiChannelBinding (and, from it, the phone number ID + decrypted
 * access token) itself, exactly the way it would for any other channel-level
 * concern.
 */
@Component
public class WhatsAppChannelAdapter implements AiChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelAdapter.class);

    private final WhatsAppApiClient whatsAppApiClient;
    private final AiChannelBindingRepository aiChannelBindingRepository;

    public WhatsAppChannelAdapter(WhatsAppApiClient whatsAppApiClient, AiChannelBindingRepository aiChannelBindingRepository) {
        this.whatsAppApiClient = whatsAppApiClient;
        this.aiChannelBindingRepository = aiChannelBindingRepository;
    }

    @Override
    public AiChannel channel() {
        return AiChannel.WHATSAPP;
    }

    @Override
    public boolean validateInbound(Object rawPayload) {
        if (!(rawPayload instanceof WhatsAppInboundContext ctx)) return false;
        JsonNode m = ctx.message();
        return m != null
                && !m.path("id").asText("").isBlank()
                && !m.path("from").asText("").isBlank()
                && !m.path("type").asText("").isBlank()
                && ctx.phoneNumberId() != null && !ctx.phoneNumberId().isBlank();
    }

    /**
     * Only ever called for a "text" message — WhatsAppWebhookService keeps
     * every other message type (image/audio/video/sticker/location/contact/
     * document/interactive) out of the AI pipeline entirely (spec §12); it
     * never reaches this method pretending to be text.
     */
    @Override
    public IncomingAiMessage normalizeInbound(Object rawPayload) {
        WhatsAppInboundContext ctx = (WhatsAppInboundContext) rawPayload;
        JsonNode m = ctx.message();

        String externalMessageId = m.path("id").asText(null);
        String fromWaId = m.path("from").asText(null);
        String text = m.path("text").path("body").asText(null);
        long timestampSeconds = parseLongOrZero(m.path("timestamp").asText(null));
        Instant timestamp = timestampSeconds > 0 ? Instant.ofEpochSecond(timestampSeconds) : Instant.now();

        // Conversation identity (spec §14): stable per customer-per-business-
        // endpoint, so the same WhatsApp user texting the same business phone
        // number always resolves back to the same AI conversation, without
        // ever creating a new one per message. Deliberately NOT the WhatsApp
        // message id (that's the per-message idempotency key, externalMessageId,
        // not the conversation key).
        String externalConversationId = ctx.phoneNumberId() + ":" + fromWaId;

        return new IncomingAiMessage(
                AiChannel.WHATSAPP,
                null, // channelBindingId is resolved by AiChannelRouter, never trusted from here
                externalConversationId,
                externalMessageId,
                fromWaId,
                text,
                timestamp,
                Map.of("phoneNumberId", ctx.phoneNumberId())
        );
    }

    @Override
    public void sendOutbound(OutgoingAiMessage message) {
        Map<String, String> metadata = message.metadata();
        if (metadata == null) {
            log.warn("Dropped an outbound WhatsApp message with no routing metadata (conversationId={})", message.conversationId());
            return;
        }
        String channelBindingIdRaw = metadata.get(OutgoingAiMessage.CHANNEL_BINDING_ID_KEY);
        String recipientWaId = metadata.get(OutgoingAiMessage.RECIPIENT_EXTERNAL_USER_ID_KEY);
        if (channelBindingIdRaw == null || recipientWaId == null) {
            log.warn("Dropped an outbound WhatsApp message missing channelBindingId/recipientWaId (conversationId={})", message.conversationId());
            return;
        }

        AiChannelBinding binding = aiChannelBindingRepository.findById(UUID.fromString(channelBindingIdRaw)).orElse(null);
        if (binding == null || !binding.isActive() || binding.getCredentialsEncrypted() == null) {
            log.warn("Dropped an outbound WhatsApp message — binding {} is missing/inactive/unconfigured", channelBindingIdRaw);
            return;
        }

        WhatsAppApiClient.WhatsAppSendResult result = whatsAppApiClient.sendTextMessage(
                binding.getExternalAccountId(), binding.getCredentialsEncrypted(), recipientWaId, message.text());

        // Never claims delivery succeeded unless Meta actually said so (spec
        // §20) — this is a log-only outcome; the AI conversation record
        // itself is untouched either way (it already has the assistant's
        // message persisted from AiChatService, regardless of whether the
        // WhatsApp send itself succeeds).
        if (result.success()) {
            log.info("WhatsApp outbound send succeeded (phoneNumberId={}, whatsappMessageId={})",
                    binding.getExternalAccountId(), result.whatsappMessageId());
        } else {
            log.error("WhatsApp outbound send FAILED (phoneNumberId={}, conversationId={}): {}",
                    binding.getExternalAccountId(), message.conversationId(), result.errorMessage());
        }
    }

    private long parseLongOrZero(String raw) {
        if (raw == null) return 0;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** One WhatsApp message object from Meta's webhook envelope, plus the endpoint metadata it arrived on. */
    public record WhatsAppInboundContext(JsonNode message, String phoneNumberId, String wabaId) {
    }
}
