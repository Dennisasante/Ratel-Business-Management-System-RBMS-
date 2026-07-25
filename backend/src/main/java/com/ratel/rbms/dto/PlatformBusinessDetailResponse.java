package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlatformBusinessDetailResponse(
        UUID id,
        String name,
        String industry,
        String location,
        String contactEmail,
        String contactPhone,
        String currency,
        String subscriptionPlan,
        List<String> enabledModules,
        boolean active,
        Instant createdAt,
        List<UserResponse> users,
        int productCount,
        int customerCount,
        int salesCount,
        BigDecimal totalRevenue,
        int expenseCount,
        BigDecimal totalExpenses
) {
}
