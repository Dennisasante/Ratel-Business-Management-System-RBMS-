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
        Instant createdAt,
        // Needed by the Billing page's own "I've paid — check again" action on a
        // still-PENDING row (see BillingPage/handleRetryVerify) — the same
        // reference the owner already has on their own Paystack receipt, so
        // exposing it back to them isn't revealing anything new.
        String paystackReference
) {
    public static SubscriptionPaymentResponse from(SubscriptionPayment payment, String planName) {
        return new SubscriptionPaymentResponse(
                payment.getId(), planName, payment.getAmount(), payment.getCurrency(), payment.getMonths(), payment.getStatus(),
                payment.getPeriodStart(), payment.getPeriodEnd(), payment.getPaidAt(), payment.getCreatedAt(),
                payment.getPaystackReference()
        );
    }
}
