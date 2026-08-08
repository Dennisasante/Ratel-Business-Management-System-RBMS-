package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomWigRequestResponse(
        UUID id,
        long requestNumber,
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        BigDecimal estimatedPrice,
        String status,
        BigDecimal finalPrice,
        String whatsappLink,
        Instant createdAt
) {
}
