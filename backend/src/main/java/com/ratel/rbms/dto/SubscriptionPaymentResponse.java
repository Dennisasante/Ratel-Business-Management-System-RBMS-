package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SubscriptionPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPaymentResponse(
        UUID id,
        String planName,
        BigDecimal amount,
        String currency,
        int months,
        String status,
        Instant periodStart,
        Instant periodEnd,
        Instant paidAt,
        Instant createdAt
) {
    public static SubscriptionPaymentResponse from(SubscriptionPayment payment, String planName) {
        return new SubscriptionPaymentResponse(
                payment.getId(), planName, payment.getAmount(), payment.getCurrency(), payment.getMonths(), payment.getStatus(),
                payment.getPeriodStart(), payment.getPeriodEnd(), payment.getPaidAt(), payment.getCreatedAt()
        );
    }
}
