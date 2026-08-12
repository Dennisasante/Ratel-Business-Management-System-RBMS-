package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlatformServiceOrderSummaryResponse(
        UUID id,
        Long orderNumber,
        String customerName,
        BigDecimal price,
        String status,
        String paymentStatus,
        Instant receivedAt
) {
}
