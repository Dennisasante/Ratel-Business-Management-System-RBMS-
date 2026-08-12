package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlatformSaleSummaryResponse(
        UUID id,
        Long saleNumber,
        String customerName,
        BigDecimal totalAmount,
        String paymentStatus,
        Instant createdAt
) {
}
