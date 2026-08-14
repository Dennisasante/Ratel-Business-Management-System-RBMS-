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
        String description,
        BigDecimal estimatedPrice,
        String status,
        BigDecimal finalPrice,
        String paymentStatus,
        BigDecimal amountPaid,
        // Derived — finalPrice minus amountPaid, clamped to zero. Null (not
        // zero) when there's no finalPrice yet, since "owed" is meaningless
        // before a price is agreed.
        BigDecimal balanceDue,
        String whatsappLink,
        String source,
        Instant createdAt
) {
}
