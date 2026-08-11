package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BillingStatusResponse(
        String billingStatus,
        SubscriptionPlanResponse plan,
        Instant trialEndsAt,
        Instant currentPeriodEndsAt,
        // Only meaningful (non-null) while billingStatus == GRACE.
        Instant gracePeriodEndsAt,
        long daysRemaining,
        // Super-admin-set, GHS-per-USD — null hides the display toggle on the
        // Billing page entirely. Riding along on /billing/status rather than a
        // dedicated endpoint since it's the one business-facing read businesses
        // are allowed to see of platform_billing_settings.
        BigDecimal usdDisplayRate
) {
}
