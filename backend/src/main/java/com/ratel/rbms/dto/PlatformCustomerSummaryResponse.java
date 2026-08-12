package com.ratel.rbms.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformCustomerSummaryResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        Instant createdAt
) {
}
