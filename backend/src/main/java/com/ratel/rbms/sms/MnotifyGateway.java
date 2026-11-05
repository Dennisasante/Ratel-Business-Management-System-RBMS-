package com.ratel.rbms.sms;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * mNotify's REST API. Endpoints and field names (POST /sms/quick with
 * recipient[]/sender/message/is_schedule/schedule_date, GET /balance/sms,
 * the key as a ?key= query parameter) were taken from two independent
 * open-source mNotify client libraries, since mNotify's own docs site is a
 * script-rendered page that couldn't be read directly. The response shapes
 * below are parsed defensively for that reason — treat the first real send as
 * the confirmation of the contract.
 *
 * SECURITY: the API key rides in the URL, and Spring's exception messages
 * include the full request URL — so nothing here ever logs or rethrows a
 * RestClientException's message. Only its class name is logged.
 *
 * This is the default provider (see SmsGatewayConfig): production never
 * silently falls back to the mock. With no key configured it simply reports
 * isConfigured() == false and every send is refused.
 */
public class MnotifyGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(MnotifyGateway.class);

    // Asked for on every call, so a key saved while the app is running takes effect at once.
    private final java.util.function.Supplier<String> apiKeySource;
    private final RestClient client;

    public MnotifyGateway(java.util.function.Supplier<String> apiKeySource, String baseUrl) {
        this(apiKeySource, buildClient(baseUrl));
    }

    public MnotifyGateway(String apiKey, String baseUrl) {
        this(() -> apiKey, buildClient(baseUrl));
    }

    // For tests: lets a MockRestServiceServer be bound to the client.
    MnotifyGateway(String apiKey, RestClient client) {
        this(() -> apiKey, client);
    }

    MnotifyGateway(java.util.function.Supplier<String> apiKeySource, RestClient client) {
        this.apiKeySource = apiKeySource;
        this.client = client;
    }

    private String apiKey() {
        String key = apiKeySource.get();
        return key == null ? "" : key.trim();
    }

    private static RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey().isBlank();
    }

    @Override
    public String name() {
        return "mnotify";
    }

    @Override
    public SendResult send(String senderId, String recipient, String message) {
        if (!isConfigured()) {
            return SendResult.rejected("SMS provider isn't configured.");
        }

        Map<String, Object> body = Map.of(
                "recipient", List.of(recipient),
                "sender", senderId,
                "message", message,
                "is_schedule", false,
                "schedule_date", ""
        );

        final String key = apiKey();
        JsonNode response;
        try {
            response = client.post()
                    .uri(u -> u.path("/sms/quick").queryParam("key", key).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    // mNotify reports rejections (bad sender ID, empty balance...) in the body,
                    // sometimes with a non-2xx status — read it instead of throwing it away.
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("mNotify send failed ({})", e.getClass().getSimpleName());
            return SendResult.rejected("Couldn't reach the SMS provider.");
        }

        if (response == null) {
            return SendResult.rejected("Empty response from the SMS provider.");
        }

        String status = response.path("status").asText("");
        String code = response.path("code").asText("");
        if ("success".equalsIgnoreCase(status) || "2000".equals(code)) {
            String ref = response.path("summary").path("_id").asText(null);
            return SendResult.ok(ref != null && !ref.isBlank() ? ref : null);
        }

        String providerMessage = response.path("message").asText("rejected");
        // A rejection here is often Tallia's OWN mNotify balance running out or a
        // sender ID problem — the operator needs to see that, the business does not.
        log.error("mNotify rejected a send: code={} message={}", code, providerMessage);
        return SendResult.rejected(("[" + code + "] " + providerMessage).trim());
    }

    /**
     * POST /senderid/status. The field name isn't confirmed (mNotify's own docs
     * couldn't be read), so both likely spellings are sent, and the reply is read
     * loosely: any "approved" -> APPROVED, "pending"/"processing" -> PENDING,
     * "reject"/"not found"/"invalid" -> NOT_FOUND, anything else -> UNKNOWN so a
     * surprising answer never silently flips a business's status.
     */
    @Override
    public SenderCheck checkSender(String senderId) {
        if (!isConfigured()) {
            return new SenderCheck(SenderState.UNKNOWN, "SMS provider isn't configured.");
        }
        final String key = apiKey();
        JsonNode response;
        try {
            response = client.post()
                    .uri(u -> u.path("/senderid/status").queryParam("key", key).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("sender_name", senderId, "sender_id", senderId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("mNotify sender check failed ({})", e.getClass().getSimpleName());
            return new SenderCheck(SenderState.UNKNOWN, "Couldn't reach mNotify.");
        }
        if (response == null) {
            return new SenderCheck(SenderState.UNKNOWN, "Empty answer from mNotify.");
        }

        String words = (response.path("summary").toString() + " " + response.path("message").asText("")
                + " " + response.path("status").asText("")).toLowerCase();
        if (words.contains("approved")) {
            return new SenderCheck(SenderState.APPROVED, "mNotify says approved.");
        }
        if (words.contains("pending") || words.contains("processing") || words.contains("review")) {
            return new SenderCheck(SenderState.PENDING, "mNotify says it's still being reviewed.");
        }
        if (words.contains("reject") || words.contains("not found") || words.contains("invalid") || words.contains("no sender")) {
            return new SenderCheck(SenderState.NOT_FOUND, "mNotify doesn't have an approved sender with that name.");
        }
        log.warn("mNotify sender check gave an answer I can't classify: code={} status={}",
                response.path("code").asText(""), response.path("status").asText(""));
        return new SenderCheck(SenderState.UNKNOWN, "mNotify answered something unexpected — check the sender name in your mNotify dashboard.");
    }

    @Override
    public Optional<ProviderBalance> balance() {
        if (!isConfigured()) return Optional.empty();
        final String key = apiKey();
        try {
            JsonNode response = client.get()
                    .uri(u -> u.path("/balance/sms").queryParam("key", key).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(JsonNode.class);
            if (response == null || !response.has("balance")) return Optional.empty();
            BigDecimal balance = new BigDecimal(response.path("balance").asText("0"));
            BigDecimal bonus = new BigDecimal(response.path("bonus").asText("0"));
            return Optional.of(new ProviderBalance(balance, bonus));
        } catch (RestClientException | NumberFormatException e) {
            log.error("mNotify balance check failed ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
