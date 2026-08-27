package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin wrapper around the official WhatsApp Cloud API (Meta Graph API) —
 * same "direct REST call via Spring's RestClient, no third-party SDK"
 * posture as PaystackService/OpenAiProvider. Never Twilio, never an
 * unofficial API, never WhatsApp Web automation.
 *
 * Deliberately knows nothing about businesses, conversations, or AI — it's
 * a dumb pipe to Meta, exactly like PaystackService is a dumb pipe to
 * Paystack. WhatsAppChannelAdapter/WhatsAppBindingService own what to send
 * and to which business's credentials; this class only ever takes an
 * explicit phoneNumberId + accessToken per call — there is no "current
 * business" concept here, so a business can never accidentally use another
 * business's token (each call is handed its own credential explicitly).
 *
 * The Graph API version is the ONLY place that string appears — see
 * app.whatsapp.graph-api-version in application.yml.
 */
@Service
public class WhatsAppApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppApiClient.class);

    private final ObjectMapper objectMapper;
    private final String graphApiVersion;
    private final String baseUrl;

    public WhatsAppApiClient(
            ObjectMapper objectMapper,
            @Value("${app.whatsapp.graph-api-version}") String graphApiVersion,
            // Overridable ONLY so tests can point this at a local stub server
            // instead of the real Meta API — every real deployment uses the
            // default. Never exposed as a per-business setting.
            @Value("${app.whatsapp.graph-api-base-url:https://graph.facebook.com}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.graphApiVersion = graphApiVersion;
        this.baseUrl = baseUrl;
    }

    /** Outcome of one Graph API call — never carries the access token, and never claims success unless Meta actually said so. */
    public record WhatsAppSendResult(boolean success, String whatsappMessageId, String errorMessage) {
        static WhatsAppSendResult ok(String whatsappMessageId) {
            return new WhatsAppSendResult(true, whatsappMessageId, null);
        }

        static WhatsAppSendResult failure(String errorMessage) {
            return new WhatsAppSendResult(false, null, errorMessage);
        }
    }

    public record PhoneNumberMetadata(boolean valid, String displayPhoneNumber, String verifiedName, String errorMessage) {
    }

    /**
     * POST /{phoneNumberId}/messages — sends a plain text message. Never
     * logs accessToken, never includes it in a returned/thrown message;
     * a rejected send is reported as a plain failure, never as a claim of
     * success.
     */
    public WhatsAppSendResult sendTextMessage(String phoneNumberId, String accessToken, String recipientWaId, String text) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("to", recipientWaId);
        body.put("type", "text");
        ObjectNode textNode = body.putObject("text");
        textNode.put("body", text);

        JsonNode response;
        try {
            response = client(accessToken)
                    .post()
                    .uri("/{version}/{phoneNumberId}/messages", graphApiVersion, phoneNumberId)
                    .body(body)
                    .retrieve()
                    // Meta answers a rejected send (bad token, invalid recipient,
                    // rate limit) with a real error body — read it below instead
                    // of letting the default handler swallow it.
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // Never include the exception's own message verbatim if it could
            // ever echo back request details — log a safe, fixed summary only.
            log.error("WhatsApp send failed for phoneNumberId={} (network/transport error)", phoneNumberId);
            return WhatsAppSendResult.failure("Couldn't reach the WhatsApp API.");
        }

        if (response == null) {
            return WhatsAppSendResult.failure("Couldn't reach the WhatsApp API.");
        }
        if (response.has("error")) {
            String message = response.path("error").path("message").asText("WhatsApp rejected this message.");
            log.warn("WhatsApp API rejected an outbound message for phoneNumberId={}: {}", phoneNumberId, message);
            return WhatsAppSendResult.failure(message);
        }

        String whatsappMessageId = response.path("messages").path(0).path("id").asText(null);
        return WhatsAppSendResult.ok(whatsappMessageId);
    }

    /**
     * GET /{phoneNumberId}?fields=display_phone_number,verified_name — the
     * "Test Connection" check (spec §31). Only ever reads metadata; never
     * sends a customer-facing message as part of validation.
     */
    public PhoneNumberMetadata validatePhoneNumber(String phoneNumberId, String accessToken) {
        JsonNode response;
        try {
            response = client(accessToken)
                    .get()
                    .uri("/{version}/{phoneNumberId}?fields=display_phone_number,verified_name", graphApiVersion, phoneNumberId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("WhatsApp connection test failed for phoneNumberId={} (network/transport error)", phoneNumberId);
            return new PhoneNumberMetadata(false, null, null, "Couldn't reach the WhatsApp API.");
        }

        if (response == null) {
            return new PhoneNumberMetadata(false, null, null, "Couldn't reach the WhatsApp API.");
        }
        if (response.has("error")) {
            String message = response.path("error").path("message").asText("WhatsApp rejected this request.");
            return new PhoneNumberMetadata(false, null, null, message);
        }

        return new PhoneNumberMetadata(true,
                response.path("display_phone_number").asText(null),
                response.path("verified_name").asText(null),
                null);
    }

    private RestClient client(String accessToken) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }
}
