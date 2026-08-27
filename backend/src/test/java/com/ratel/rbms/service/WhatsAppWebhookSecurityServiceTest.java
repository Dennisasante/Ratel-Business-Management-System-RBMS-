package com.ratel.rbms.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests (no Spring context needed) for the two Meta/WhatsApp
 * webhook authenticity checks — spec §9/§34's "webhook verification" and
 * "signature validation" minimums.
 */
class WhatsAppWebhookSecurityServiceTest {

    private static final String VERIFY_TOKEN = "my-verify-token-123";
    private static final String APP_SECRET = "my-meta-app-secret";

    private final WhatsAppWebhookSecurityService service = new WhatsAppWebhookSecurityService(VERIFY_TOKEN, APP_SECRET);

    @Test
    void validVerifyTokenAndSubscribeModeAcceptsTheChallenge() {
        assertTrue(service.verifyChallenge("subscribe", VERIFY_TOKEN));
    }

    @Test
    void invalidVerifyTokenIsRejected() {
        assertFalse(service.verifyChallenge("subscribe", "wrong-token"));
    }

    @Test
    void correctTokenButWrongModeIsRejected() {
        assertFalse(service.verifyChallenge("unsubscribe", VERIFY_TOKEN));
    }

    @Test
    void blankConfiguredTokenAlwaysFailsClosed() {
        WhatsAppWebhookSecurityService unconfigured = new WhatsAppWebhookSecurityService("", APP_SECRET);
        assertFalse(unconfigured.verifyChallenge("subscribe", "anything"));
    }

    @Test
    void validSignatureIsAccepted() {
        String body = "{\"entry\":[]}";
        String signature = "sha256=" + hmacHex(body, APP_SECRET);
        assertTrue(service.verifySignature(body, signature));
    }

    @Test
    void invalidSignatureIsRejected() {
        String body = "{\"entry\":[]}";
        assertFalse(service.verifySignature(body, "sha256=0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void signatureComputedWithTheWrongSecretIsRejected() {
        String body = "{\"entry\":[]}";
        String signature = "sha256=" + hmacHex(body, "a-different-secret");
        assertFalse(service.verifySignature(body, signature));
    }

    @Test
    void tamperedBodyInvalidatesAnOtherwiseValidSignature() {
        String originalBody = "{\"entry\":[{\"id\":\"1\"}]}";
        String signature = "sha256=" + hmacHex(originalBody, APP_SECRET);
        String tamperedBody = "{\"entry\":[{\"id\":\"2\"}]}";
        assertFalse(service.verifySignature(tamperedBody, signature));
    }

    @Test
    void missingSignatureHeaderIsRejected() {
        assertFalse(service.verifySignature("{}", null));
        assertFalse(service.verifySignature("{}", ""));
    }

    @Test
    void blankConfiguredAppSecretAlwaysFailsClosed() {
        WhatsAppWebhookSecurityService unconfigured = new WhatsAppWebhookSecurityService(VERIFY_TOKEN, "");
        assertFalse(unconfigured.verifySignature("{}", "sha256=anything"));
    }

    private String hmacHex(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
