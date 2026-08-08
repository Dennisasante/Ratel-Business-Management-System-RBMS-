package com.ratel.rbms.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts business-supplied third-party API secrets (Paystack/WooCommerce
 * keys, see BusinessIntegrations) at rest with AES-256-GCM. These are live
 * payment credentials for potentially many separate businesses sharing one
 * database — meaningfully more sensitive than anything else this app stores
 * in a column, so plaintext isn't good enough here even though nothing else
 * in the app encrypts DB columns (JWT secret, DB password, etc. are env vars
 * that never touch the database at all).
 *
 * Reads ENCRYPTION_KEY directly from the process environment rather than via
 * Spring's @Value injection: JPA/Hibernate instantiates AttributeConverters
 * itself through a no-arg constructor, bypassing Spring's dependency
 * injection entirely (confirmed empirically — a Spring-managed @Component
 * variant of this class left the key null at the point Hibernate actually
 * used it). Reading the environment directly sidesteps that bean-wiring
 * question altogether, at the cost of not going through Spring's
 * `${app.encryption-key}` property-resolution layer — acceptable since every
 * other secret in this app (JWT_SECRET, PAYSTACK_SECRET_KEY, ...) is also
 * always supplied as a raw environment variable in practice, never via
 * application.yml directly.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecretKeySpec KEY = deriveKeyFromEnvironment();

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        requireKey();
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't encrypt value", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        requireKey();
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] cipherText = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't decrypt value", e);
        }
    }

    private static SecretKeySpec deriveKeyFromEnvironment() {
        String passphrase = System.getenv("ENCRYPTION_KEY");
        if (passphrase == null || passphrase.isBlank()) {
            return null; // fail loudly at point of use, not with a default key nobody chose
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] derived = sha256.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Couldn't derive encryption key", e);
        }
    }

    private void requireKey() {
        if (KEY == null) {
            throw new IllegalStateException("ENCRYPTION_KEY isn't configured — set it before storing integration secrets.");
        }
    }
}
