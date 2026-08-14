package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Same "null = leave unchanged" convention used across this codebase's other
 * partial-update DTOs. clearPlan/clearPriceOverride exist because "set back
 * to no plan / no override" can't be expressed by a null field under that
 * convention — they're the explicit escape hatch for it.
 *
 * priceOverride is interpreted downstream (BillingService) as the business's
 * negotiated MONTHLY rate, not a flat total — multi-month discount tiers
 * still apply on top of it.
 */
public record PlatformBusinessBillingUpdateRequest(
        UUID subscriptionPlanId,
        boolean clearPlan,
        Instant trialEndsAt,
        BigDecimal priceOverride,
        boolean clearPriceOverride
) {
}
