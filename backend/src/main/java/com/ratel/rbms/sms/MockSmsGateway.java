package com.ratel.rbms.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Local-development provider: logs instead of sending anything, so the whole
 * wallet/send flow can be exercised with no mNotify account and no real SMS
 * cost. Active ONLY when explicitly selected (SMS_PROVIDER=mock) — unlike the
 * AI mock, it is never the default, because a deployment that quietly "sent"
 * nothing would tell a customer their message went out when it didn't.
 *
 * To exercise the failure/refund path, send to 0200000000 — it is always rejected.
 */
public class MockSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSmsGateway.class);

    static final String ALWAYS_FAILS = "0200000000";

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public SendResult send(String senderId, String recipient, String message) {
        if (ALWAYS_FAILS.equals(recipient)) {
            return SendResult.rejected("[MOCK] simulated provider rejection");
        }
        log.info("[MOCK SMS] from={} to={} chars={}", senderId, recipient, message.length());
        return SendResult.ok("mock-" + UUID.randomUUID());
    }

    // Every sender name is "approved" in mock mode, so local testing isn't blocked on it.
    @Override
    public SenderCheck checkSender(String senderId) {
        return new SenderCheck(SenderState.APPROVED, "[MOCK] approved");
    }

    @Override
    public Optional<ProviderBalance> balance() {
        return Optional.of(new ProviderBalance(new BigDecimal("9999"), BigDecimal.ZERO));
    }
}
