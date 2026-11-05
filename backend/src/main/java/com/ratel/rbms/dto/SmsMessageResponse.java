package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SmsMessage;

import java.time.Instant;
import java.util.UUID;

// Deliberately omits the provider's own reference and raw error text — those
// can name Tallia's upstream account and are for the Super Admin only.
public record SmsMessageResponse(
        UUID id,
        String recipient,
        String message,
        String kind,
        int segments,
        int creditsUsed,
        String status,
        Instant createdAt
) {
    public static SmsMessageResponse from(SmsMessage m) {
        return new SmsMessageResponse(m.getId(), m.getRecipient(), m.getMessage(), m.getKind(),
                m.getSegments(), m.getCreditsUsed(), m.getStatus(), m.getCreatedAt());
    }
}
