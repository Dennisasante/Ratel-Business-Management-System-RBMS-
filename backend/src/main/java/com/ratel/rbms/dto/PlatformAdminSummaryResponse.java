package com.ratel.rbms.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformAdminSummaryResponse(
        UUID id,
        String fullName,
        String email,
        Instant createdAt
) {
}
