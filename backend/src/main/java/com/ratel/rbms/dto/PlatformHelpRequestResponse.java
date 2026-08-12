package com.ratel.rbms.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformHelpRequestResponse(
        UUID id,
        UUID businessId,
        String businessName,
        String requesterName,
        String requesterEmail,
        String category,
        String subject,
        String message,
        String status,
        String adminResponse,
        Instant respondedAt,
        Instant createdAt
) {
}
