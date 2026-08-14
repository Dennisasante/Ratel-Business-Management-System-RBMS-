package com.ratel.rbms.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PlatformBillingSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    private final PlatformBillingSettingsRepository platformBillingSettingsRepository;
    private final String envPlatformSecretKey;

    public PaystackService(
            PlatformBillingSettingsRepository platformBillingSettingsRepository,
            @Value("${app.paystack.secret-key}") String envPlatformSecretKey
    ) {
        this.platformBillingSettingsRepository = platformBillingSettingsRepository;
        this.envPlatformSecretKey = envPlatformSecretKey;
    }

    /**
     * RBMS's own Paystack secret key (subscription billing) — the Super Admin's
     * billing-settings UI takes priority so it can be changed without a
     * redeploy; the PAYSTACK_SECRET_KEY env var is only a fallback for local
     * dev before anyone's clicked through that page yet.
     */
    public String resolvePlatformSecretKey() {
        String dbKey = platformBillingSettingsRepository.findFirstByOrderByUpdatedAtDesc().getPaystackSecretKey();
        return dbKey != null && !dbKey.isBlank() ? dbKey : envPlatformSecretKey;
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
                    // Paystack answers a rejected checkout (bad email, an amount below
                    // its minimum, a currency not enabled on this account, a stale/
                    // revoked key, etc.) with a non-2xx status but a real, actionable
                    // `message` in the body — read it instead of letting the default
                    // handler throw it away as a generic HTTP error.
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(InitializeResponse.class);
        } catch (RestClientException e) {
            log.error("Paystack initializeTransaction call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't start checkout with Paystack. Please try again.");
        }

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't start checkout with Paystack. Please try again.");
        }

        if (!response.status() || response.data() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    response.message() != null && !response.message().isBlank()
                            ? response.message()
                            : "Couldn't start checkout with Paystack. Please try again.");
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
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(VerifyResponse.class);
        } catch (RestClientException e) {
            log.error("Paystack verifyTransaction call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't verify this payment with Paystack right now. Please try again shortly.");
        }

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't verify this payment with Paystack right now. Please try again shortly.");
        }

        if (response.data() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    response.message() != null && !response.message().isBlank()
                            ? response.message()
                            : "Couldn't verify this payment with Paystack right now. Please try again shortly.");
        }

        VerifyData data = response.data();
        boolean success = response.status() && "success".equalsIgnoreCase(data.status());

        // Not every payment method returns a reusable authorization (e.g. USSD,
        // mobile money) — null-safe throughout, callers just get nulls back.
        AuthorizationData auth = data.authorization();
        boolean reusable = auth != null && auth.reusable();
        String authorizationCode = reusable ? auth.authorizationCode() : null;
        String cardLast4 = reusable ? auth.last4() : null;
        String cardBrand = reusable ? auth.brand() : null;

        return new VerifyResult(success, data.status(), data.reference(), data.amount(), data.currency(), data.paidAt(),
                authorizationCode, cardLast4, cardBrand);
    }

    /**
     * Charges a previously-saved reusable authorization directly — no popup,
     * no customer interaction. Used for subscription auto-renewal only; the
     * caller is responsible for funneling the result through the same
     * verifyPayment()-style idempotency guard a manual checkout uses, this
     * method itself is just the Paystack call.
     */
    public ChargeResult chargeAuthorization(String secretKey, String authorizationCode, String email, long amountMinorUnits, String reference) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Paystack isn't configured.");
        }

        Map<String, Object> body = Map.of(
                "authorization_code", authorizationCode,
                "email", email,
                "amount", amountMinorUnits,
                "reference", reference,
                "currency", "GHS"
        );

        ChargeAuthorizationResponse response;
        try {
            response = client(secretKey)
                    .post()
                    .uri("/transaction/charge_authorization")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(ChargeAuthorizationResponse.class);
        } catch (RestClientException e) {
            log.error("Paystack chargeAuthorization call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach Paystack to charge the saved card.");
        }

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach Paystack to charge the saved card.");
        }

        if (response.data() == null) {
            return new ChargeResult(false, "failed", null, response.message());
        }

        ChargeAuthorizationData data = response.data();
        boolean success = response.status() && "success".equalsIgnoreCase(data.status());
        return new ChargeResult(success, data.status(), data.reference(), firstNonBlank(data.gatewayResponse(), response.message()));
    }

    /**
     * Charges a customer's mobile money wallet directly by phone number — no
     * card, no popup. Paystack sends a USSD/app approval prompt straight to
     * that phone; the customer approves it there. Confirmed against Paystack's
     * real Charge API docs (POST /charge, mobile_money.provider one of
     * mtn/atl/vod for Ghana) rather than guessed.
     *
     * Unlike initializeTransaction/verifyTransaction, a "not yet successful"
     * response here isn't a failure — Paystack's own docs show several
     * legitimate in-progress states (pending, send_otp, pay_offline) that mean
     * "the prompt is on its way to their phone, come back and verify by
     * reference once they've approved it." Only a genuine HTTP/communication
     * failure throws; call verifyTransaction(reference) afterward to resolve
     * the final PAID/FAILED outcome, same as the card checkout flow.
     *
     * Paystack can also reject a charge outright — most commonly a first-time
     * payer on a given phone/provider needing identification/verification on
     * their mobile money account before any charge attempt is even accepted —
     * with a non-2xx HTTP status and no `data` object, just a top-level
     * `message` explaining why. onStatus() below stops that from being thrown
     * away as a generic connectivity failure: it's a real, actionable, and
     * customer-specific reason (e.g. "go check your provider's app"), not
     * "we couldn't reach Paystack."
     */
    public MobileMoneyChargeResult chargeMobileMoney(String secretKey, String email, long amountMinorUnits, String phone, String provider, String reference) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Paystack isn't configured.");
        }

        Map<String, Object> body = Map.of(
                "email", email,
                "amount", amountMinorUnits,
                "reference", reference,
                "currency", "GHS",
                "mobile_money", Map.of("phone", phone, "provider", provider)
        );

        ChargeResponse response;
        try {
            response = client(secretKey)
                    .post()
                    .uri("/charge")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(ChargeResponse.class);
        } catch (RestClientException e) {
            log.error("Paystack chargeMobileMoney call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach Paystack to start the mobile money charge.");
        }

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach Paystack to start the mobile money charge.");
        }

        if (response.data() == null) {
            return new MobileMoneyChargeResult(false, "failed", null, null, response.message());
        }

        ChargeData data = response.data();
        boolean success = response.status() && "success".equalsIgnoreCase(data.status());
        return new MobileMoneyChargeResult(success, data.status(), data.reference(), data.displayText(), response.message());
    }

    /**
     * Completes a charge that came back `status: "send_otp"` — Paystack's own
     * docs (POST /charge/submit_otp) confirm this is a real, separate step from
     * the initial charge and from verifyTransaction: the customer receives an
     * SMS with a code (not a tap-to-approve prompt) for many Ghana mobile money
     * charges, and the charge stays stuck forever unless that code is submitted
     * back here. Same not-a-failure-unless-genuinely-unreachable contract as
     * chargeMobileMoney — a wrong/expired OTP comes back as a normal
     * non-"success" status, not a thrown exception.
     */
    public ChargeResult submitOtp(String secretKey, String otp, String reference) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Paystack isn't configured.");
        }

        Map<String, Object> body = Map.of("otp", otp, "reference", reference);

        ChargeAuthorizationResponse response;
        try {
            response = client(secretKey)
                    .post()
                    .uri("/charge/submit_otp")
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(ChargeAuthorizationResponse.class);
        } catch (RestClientException e) {
            log.error("Paystack submitOtp call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach Paystack to submit that code.");
        }

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach Paystack to submit that code.");
        }

        if (response.data() == null) {
            return new ChargeResult(false, "failed", null, response.message());
        }

        ChargeAuthorizationData data = response.data();
        boolean success = response.status() && "success".equalsIgnoreCase(data.status());
        return new ChargeResult(success, data.status(), data.reference(), firstNonBlank(data.gatewayResponse(), response.message()));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }

    /**
     * HMAC-SHA512 of the raw request body against the platform's own secret
     * key, compared to Paystack's x-paystack-signature header — per
     * Paystack's webhook docs. Must run against the untouched raw body bytes,
     * before any JSON parsing.
     */
    public boolean verifyWebhookSignature(String rawBody, String signatureHeader) {
        String secretKey = resolvePlatformSecretKey();
        if (signatureHeader == null || signatureHeader.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
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

    // authorizationCode/cardLast4/cardBrand are null unless Paystack returned
    // a reusable authorization on this transaction.
    public record VerifyResult(
            boolean success, String status, String reference, long amountMinorUnits, String currency, String paidAt,
            String authorizationCode, String cardLast4, String cardBrand
    ) {
    }

    // message is Paystack's own explanation of the outcome (its gateway_response
    // when present, else the top-level response message) — surfaced to the
    // caller instead of a generic "didn't work" so a wrong OTP, an expired
    // code, and a genuine API error are distinguishable in the UI.
    public record ChargeResult(boolean success, String status, String reference, String message) {
    }

    // status is Paystack's raw charge status (e.g. "success", "pending",
    // "send_otp", "pay_offline", or "failed" for an outright rejection) —
    // success is only true for "success" itself; anything else needs a
    // follow-up verifyTransaction(reference) once the customer has acted on
    // their phone. displayText is Paystack's own customer-facing instruction
    // text when present (e.g. a USSD code to dial). message is Paystack's own
    // explanation of the outcome — surfaced separately from displayText since
    // it's meant for whoever's behind the till, not the customer.
    public record MobileMoneyChargeResult(boolean success, String status, String reference, String displayText, String message) {
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
            @JsonProperty("paid_at") String paidAt,
            AuthorizationData authorization
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AuthorizationData(
            @JsonProperty("authorization_code") String authorizationCode,
            String last4,
            String brand,
            boolean reusable
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChargeAuthorizationResponse(boolean status, String message, ChargeAuthorizationData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChargeAuthorizationData(
            String status,
            String reference,
            @JsonProperty("gateway_response") String gatewayResponse
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChargeResponse(boolean status, String message, ChargeData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChargeData(String status, String reference, @JsonProperty("display_text") String displayText) {
    }
}
