package com.ratel.rbms.sms;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The one seam between Tallia and whichever SMS provider is behind it. Knows
 * nothing about businesses, credits or customers — SmsService owns all of
 * that and treats this as a dumb pipe, same posture as PaystackService.
 */
public interface SmsGateway {

    /** False when the provider has no credentials — sends are refused outright rather than pretending to succeed. */
    boolean isConfigured();

    /** Sends one message. Never throws for a provider-side rejection — that comes back as a non-accepted result. */
    SendResult send(String senderId, String recipient, String message);

    /** Tallia's own balance with the provider (not any business's). Empty if unavailable. */
    Optional<ProviderBalance> balance();

    /** A short label for the Super Admin page, e.g. "mnotify" or "mock". */
    String name();

    /** Asks the provider whether a sender name is approved. UNKNOWN means "couldn't tell" — callers must not treat it as a no. */
    default SenderCheck checkSender(String senderId) {
        return new SenderCheck(SenderState.UNKNOWN, "This provider can't check sender names.");
    }

    enum SenderState { APPROVED, PENDING, NOT_FOUND, UNKNOWN }

    record SenderCheck(SenderState state, String detail) {
    }

    record SendResult(boolean accepted, String providerRef, String error) {
        public static SendResult ok(String providerRef) {
            return new SendResult(true, providerRef, null);
        }

        public static SendResult rejected(String error) {
            return new SendResult(false, null, error);
        }
    }

    record ProviderBalance(BigDecimal balance, BigDecimal bonus) {
    }
}
