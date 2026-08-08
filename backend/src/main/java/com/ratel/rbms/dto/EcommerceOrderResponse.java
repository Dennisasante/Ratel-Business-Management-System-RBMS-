package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EcommerceOrderResponse(
        UUID id,
        String orderNumber,
        String status,
        String customerName,
        String customerEmail,
        String customerPhone,
        BigDecimal totalAmount,
        String currency,
        int itemCount,
        String whatsappLink,
        Instant createdAt,
        Instant updatedAt
) {
}
