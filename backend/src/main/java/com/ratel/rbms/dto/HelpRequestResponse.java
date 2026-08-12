package com.ratel.rbms.dto;

import java.time.Instant;
import java.util.UUID;

public record HelpRequestResponse(
        UUID id,
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
