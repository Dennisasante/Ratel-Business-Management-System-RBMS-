package com.ratel.rbms.dto;

import java.time.Instant;
import java.util.UUID;

public record SmsCampaignSummaryResponse(
        UUID id,
        String name,
        // SENDING / DONE / CANCELLED
        String status,
        String message,
        int recipients,
        int sent,
        int failed,
        int waiting,
        int cancelled,
        int creditsReserved,
        int creditsRefunded,
        // What it actually cost: reserved minus what was handed back.
        int creditsUsed,
        Instant createdAt,
        Instant finishedAt
) {
}
