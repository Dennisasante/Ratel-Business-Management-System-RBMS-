package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EcommerceOrderDetailResponse(
        UUID id,
        String orderNumber,
        String status,
        String customerName,
        String customerEmail,
        String customerPhone,
        BigDecimal totalAmount,
        String currency,
        String whatsappLink,
        List<EcommerceOrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
