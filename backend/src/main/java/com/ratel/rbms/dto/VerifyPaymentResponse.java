package com.ratel.rbms.dto;

import java.time.Instant;

public record VerifyPaymentResponse(
        boolean success,
        String billingStatus,
        Instant currentPeriodEndsAt,
        String message
) {
}
