package com.ratel.rbms.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratel.rbms.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Thin wrapper around Paystack's REST API. Deliberately knows nothing about
 * businesses, plans, or billing_status — BillingService owns that logic and
 * treats this as a dumb pipe to Paystack, which is what makes it easy to test
 * against Paystack's test keys in isolation before any billing logic exists.
 *
 * initializeTransaction/verifyTransaction take the secret key as an explicit
 * parameter rather than a fixed constructor-injected one — BillingService
 * passes the platform's own key (RBMS's subscription billing), while
 * BookingService passes a business's own key (their customers paying them
 * directly) — same Paystack API, different account each time. Only
 * verifyWebhookSignature stays tied to the platform's own key, since that's
 * always Ratel's own webhook, never a business's.
 */
@Service
public class PaystackService {

    private static final Logger log = LoggerFactory.getLogger(PaystackService.class);
    private static final String BASE_URL = "https://api.paystack.co";

    private final String platformSecretKey;

    public PaystackService(@Value("${app.paystack.secret-key}") String platformSecretKey) {
        this.platformSecretKey = platformSecretKey;
    }

    /**
     * Starts a transaction with Paystack and returns the access code the frontend's
     * Inline JS popup needs to open checkout without ever leaving the page.
     *
     * @param secretKey        the Paystack account to charge through — the platform's
     *                          own key for subscription billing, or a business's own
     *                          key for their customers' bookings/orders.
     * @param amountMinorUnits  the charge amount in the currency's smallest unit
     *                          (pesewas for GHS — 100 per cedi), never the major unit.
     */
    public InitResult initializeTransaction(String secretKey, String email, long amountMinorUnits, String reference, Map<String, Object> metadata) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Paystack isn't configured.");
        }

        Map<String, Object> body = Map.of(
                "email", email,
                "amount", amountMinorUnits,
                "reference", reference,
                "currency", "GHS",
                "metadata", metadata
        );

        InitializeResponse response;
        try {
            response = client(secretKey)
                    .post()
                    .uri("/transaction/initialize")
                    .body(body)
                    .retrieve()
                    .body(InitializeResponse.class);
        } catch (RestClientException e) {
            log.error("Paystack initializeTransaction call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't start checkout with Paystack. Please try again.");
        }

        if (response == null || !response.status() || response.data() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't start checkout with Paystack. Please try again.");
        }

        return new InitResult(response.data().accessCode(), response.data().reference(), response.data().authorizationUrl());
    }

    /**
     * Always re-checks with Paystack directly — the caller must never trust a
     * client-reported "success" for this. Returns a result rather than throwing
     * on a merely-unsuccessful payment; only a genuine API/communication failure
     * throws.
     */
    public VerifyResult verifyTransaction(String secretKey, String reference) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Paystack isn't configured.");
        }

        VerifyResponse response;
        try {
            response = client(secretKey)
                    .get()
                    .uri("/transaction/verify/{reference}", reference)
                    .retrieve()
                    .body(VerifyResponse.class);
        } catch (RestClientException e) {
            log.error("Paystack verifyTransaction call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't verify this payment with Paystack right now. Please try again shortly.");
        }

        if (response == null || response.data() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't verify this payment with Paystack right now. Please try again shortly.");
        }

        VerifyData data = response.data();
        boolean success = response.status() && "success".equalsIgnoreCase(data.status());
        return new VerifyResult(success, data.status(), data.reference(), data.amount(), data.currency(), data.paidAt());
    }

    /**
     * HMAC-SHA512 of the raw request body against the platform's own secret
     * key, compared to Paystack's x-paystack-signature header — per
     * Paystack's webhook docs. Must run against the untouched raw body bytes,
     * before any JSON parsing.
     */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank() || platformSecretKey == null || platformSecretKey.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(platformSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return computed.equalsIgnoreCase(signatureHeader.trim());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    private RestClient client(String secretKey) {
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                .build();
    }

    public record InitResult(String accessCode, String reference, String authorizationUrl) {
    }

    public record VerifyResult(boolean success, String status, String reference, long amountMinorUnits, String currency, String paidAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InitializeResponse(boolean status, String message, InitializeData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InitializeData(
            @JsonProperty("authorization_url") String authorizationUrl,
            @JsonProperty("access_code") String accessCode,
            String reference
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VerifyResponse(boolean status, String message, VerifyData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VerifyData(
            String status,
            String reference,
            long amount,
            String currency,
            @JsonProperty("paid_at") String paidAt
    ) {
    }
}
