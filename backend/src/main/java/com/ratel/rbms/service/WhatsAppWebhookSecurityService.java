package com.ratel.rbms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The two authenticity checks the Meta/WhatsApp webhook contract requires —
 * kept separate from WhatsAppWebhookController/WhatsAppWebhookService so
 * neither of those needs to know HMAC details. Mirrors
 * PaystackService.verifyWebhookSignature's own HMAC-hex-compare shape, with
 * SHA-256 (what Meta's X-Hub-Signature-256 header uses) instead of
 * Paystack's SHA-512, and a constant-time comparison since this guards a
 * publicly-reachable, unauthenticated endpoint.
 *
 * Never logs webhookVerifyToken or appSecret. A blank appSecret means
 * verifySignature always returns false (fail closed), never "trust
 * everything" — same posture as EncryptedStringConverter refusing to run
 * with no ENCRYPTION_KEY rather than silently using a default.
 */
@Service
public class WhatsAppWebhookSecurityService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookSecurityService.class);

    private final String webhookVerifyToken;
    private final String appSecret;

    public WhatsAppWebhookSecurityService(
            @Value("${app.whatsapp.webhook-verify-token}") String webhookVerifyToken,
            @Value("${app.whatsapp.app-secret}") String appSecret
    ) {
        this.webhookVerifyToken = webhookVerifyToken;
        this.appSecret = appSecret;
    }

    /**
     * Meta's one-time GET verification handshake: hub.mode must be
     * "subscribe" and hub.verify_token must match the token you entered
     * into your own Meta App's webhook config — never accepts an arbitrary
     * challenge from a request that doesn't present the right token.
     */
    public boolean verifyChallenge(String mode, String token) {
        if (webhookVerifyToken == null || webhookVerifyToken.isBlank()) {
            log.warn("WhatsApp webhook verification requested but WHATSAPP_WEBHOOK_VERIFY_TOKEN isn't configured.");
            return false;
        }
        return "subscribe".equals(mode) && constantTimeEquals(webhookVerifyToken, token);
    }

    /**
     * Every real event POST must carry a valid X-Hub-Signature-256:
     * sha256=&lt;hex HMAC of the raw request body, keyed with the Meta App
     * Secret&gt;. Computed over the exact raw bytes Meta sent — never the
     * re-serialized/parsed JSON, since re-serialization can legitimately
     * produce different bytes (key order, whitespace) and would make a
     * genuine signature look invalid.
     */
    public boolean verifySignature(String rawBody, String signatureHeader) {
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("Rejected a WhatsApp webhook because META_APP_SECRET isn't configured.");
            return false;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String prefix = "sha256=";
        String provided = signatureHeader.startsWith(prefix) ? signatureHeader.substring(prefix.length()) : signatureHeader;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return constantTimeEquals(computed, provided.trim());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.toLowerCase().getBytes(StandardCharsets.UTF_8),
                b.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
