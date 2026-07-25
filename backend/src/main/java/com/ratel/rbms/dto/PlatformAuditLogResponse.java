package com.ratel.rbms.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformAuditLogResponse(
        UUID id,
        String action,
        UUID businessId,
        String businessName,
        String adminName,
        Instant createdAt
) {
}
