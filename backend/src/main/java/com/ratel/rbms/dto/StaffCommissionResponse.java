package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StaffCommissionResponse(
        UUID userId,
        String userName,
        BigDecimal commissionRate,
        int salesCount,
        BigDecimal totalSales,
        BigDecimal commissionEarned
) {
}
