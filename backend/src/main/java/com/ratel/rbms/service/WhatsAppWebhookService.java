package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Everything the WhatsApp webhook needs to do BEFORE (and instead of, for an
 * unsupported case) reaching the AI core — verification, signature check,
 * envelope parsing, binding/module resolution, and the "unsupported message
 * type" short-circuit. WhatsAppWebhookController stays a thin pass-through
 * to this class (spec §8); this class itself contains no AI prompt, no
 * booking/customer query, and never calls AiToolService directly — the only
 * AI-adjacent call it ever makes is handing an already-normalized message to
 * {@link AiChannelRouter#routeExternal}, exactly like any other channel would.
 *
 * Never lets a malformed/unexpected third-party payload crash the request
 * (spec §33) — every per-message step is wrapped so one bad entry can't stop
 * the rest of the webhook (or the whole app) from being handled/acknowledged.
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);
    private static final String UNSUPPORTED_MESSAGE_REPLY =
            "I can currently help with text messages. Please send me your question as a text message.";

    private final WhatsAppWebhookSecurityService securityService;
    private final AiChannelBindingRepository aiChannelBindingRepository;
    private final ModuleAccessService moduleAccessService;
    private final WhatsAppChannelAdapter whatsAppChannelAdapter;
    private final AiChannelRouter aiChannelRouter;
    private final AiChannelDeliveryService aiChannelDeliveryService;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookService(
            WhatsAppWebhookSecurityService securityService,
            AiChannelBindingRepository aiChannelBindingRepository,
            ModuleAccessService moduleAccessService,
            WhatsAppChannelAdapter whatsAppChannelAdapter,
            AiChannelRouter aiChannelRouter,
            AiChannelDeliveryService aiChannelDeliveryService,
            ObjectMapper objectMapper
    ) {
        this.securityService = securityService;
        this.aiChannelBindingRepository = aiChannelBindingRepository;
        this.moduleAccessService = moduleAccessService;
        this.whatsAppChannelAdapter = whatsAppChannelAdapter;
        this.aiChannelRouter = aiChannelRouter;
        this.aiChannelDeliveryService = aiChannelDeliveryService;
        this.objectMapper = objectMapper;
    }

    /** Meta's one-time GET verification handshake. Returns the challenge only when the token is genuinely valid; null otherwise. */
    public String handleVerification(String mode, String token, String challenge) {
        if (securityService.verifyChallenge(mode, token)) {
            log.info("WhatsApp webhook verification succeeded.");
            return challenge;
        }
        log.warn("Rejected a WhatsApp webhook verification attempt (invalid mode/token).");
        return null;
    }

    /**
     * A real event POST. Throws ApiException(401) ONLY for a bad signature
     * (the one case the spec requires an outright reject); every other
     * "can't/shouldn't process this" case (unknown account, inactive
     * binding, AI disabled, malformed entry) is handled by logging and
     * simply not processing further — the controller always acks 200 for
     * those, matching Meta's own retry semantics (a non-2xx makes Meta
     * retry aggressively, which would never fix an unknown-account event).
     */
    public void handleEvent(String rawBody, String signatureHeader) {
        if (!securityService.verifySignature(rawBody, signatureHeader)) {
            log.warn("Rejected a WhatsApp webhook event: invalid signature.");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid signature.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            log.error("Failed to parse a WhatsApp webhook payload as JSON — ignoring.");
            return;
        }

        try {
            processEnvelope(root);
        } catch (Exception e) {
            log.error("Unexpected error while processing a WhatsApp webhook envelope", e);
        }
    }

    private void processEnvelope(JsonNode root) {
        for (JsonNode entry : root.path("entry")) {
            String wabaId = entry.path("id").asText(null);
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asText(null);
                if (phoneNumberId == null || phoneNumberId.isBlank()) {
                    continue;
                }

                JsonNode messages = value.path("messages");
                if (!messages.isArray() || messages.isEmpty()) {
                    // Delivery/read status callbacks and other non-message
                    // updates land here too — nothing to process, not an error.
                    continue;
                }

                for (JsonNode message : messages) {
                    try {
                        processOneMessage(phoneNumberId, wabaId, message);
                    } catch (Exception e) {
                        log.error("Error processing one WhatsApp message (messageId={})", safeMessageId(message), e);
                    }
                }
            }
        }
    }

    private void processOneMessage(String phoneNumberId, String wabaId, JsonNode message) {
        String messageId = safeMessageId(message);
        String type = message.path("type").asText("unknown");
        log.info("WhatsApp webhook received (phoneNumberId={}, messageId={}, type={})", phoneNumberId, messageId, type);

        AiChannelBinding binding = aiChannelBindingRepository
                .findByChannelAndExternalAccountId(AiChannel.WHATSAPP, phoneNumberId)
                .orElse(null);
        if (binding == null || !binding.isActive()) {
            // Never resolve a business merely because the payload contains
            // an identifier — an unrecognized/inactive phone number id is
            // rejected safely, not guessed at (spec §10).
            log.warn("WhatsApp webhook: no active channel binding for phoneNumberId={} — ignoring (messageId={})", phoneNumberId, messageId);
            return;
        }
        log.info("WhatsApp webhook: binding resolved (business={}, messageId={})", binding.getBusinessId(), messageId);

        if (!moduleAccessService.hasModule(binding.getBusinessId(), "AI")) {
            log.info("WhatsApp webhook: AI module disabled for business {} — ignoring (messageId={})", binding.getBusinessId(), messageId);
            return;
        }

        if (!"text".equals(type)) {
            log.info("WhatsApp webhook: unsupported message type '{}' — not invoking AI (messageId={})", type, messageId);
            String fromWaId = message.path("from").asText(null);
            if (fromWaId != null) {
                aiChannelDeliveryService.deliver(new OutgoingAiMessage(
                        null, AiChannel.WHATSAPP, UNSUPPORTED_MESSAGE_REPLY, messageId,
                        Map.of(
                                OutgoingAiMessage.CHANNEL_BINDING_ID_KEY, binding.getId().toString(),
                                OutgoingAiMessage.RECIPIENT_EXTERNAL_USER_ID_KEY, fromWaId
                        )));
            }
            return;
        }

        var ctx = new WhatsAppChannelAdapter.WhatsAppInboundContext(message, phoneNumberId, wabaId);
        if (!whatsAppChannelAdapter.validateInbound(ctx)) {
            log.warn("WhatsApp webhook: malformed text message payload — ignoring (messageId={})", messageId);
            return;
        }

        IncomingAiMessage incoming = whatsAppChannelAdapter.normalizeInbound(ctx);
        log.info("WhatsApp webhook: processing started (messageId={})", messageId);
        aiChannelRouter.routeExternal(incoming, phoneNumberId);
        log.info("WhatsApp webhook: processing completed (messageId={})", messageId);
    }

    private String safeMessageId(JsonNode message) {
        return message.path("id").asText("unknown");
    }
}
