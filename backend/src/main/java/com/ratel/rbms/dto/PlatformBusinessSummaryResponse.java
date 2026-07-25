package com.ratel.rbms.dto;

import java.time.Instant;
import java.util.UUID;

public record PlatformBusinessSummaryResponse(
        UUID id,
        String name,
        String industry,
        String location,
        String subscriptionPlan,
        boolean active,
        int userCount,
        String ownerEmail,
        Instant createdAt
) {
}
