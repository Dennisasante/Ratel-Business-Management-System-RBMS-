package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingDetailResponse(
        long bookingNumber,
        String businessName,
        String serviceName,
        String status,
        Instant scheduledAt,
        BigDecimal price,
        String paymentStatus,
        String customerName,
        BigDecimal amountDue,
        String currency,
        String businessWhatsappLink,
        String customerLocation,
        int cancellationCutoffHours
) {
}
