package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SubscriptionPlan;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionPlanResponse(
        UUID id,
        String name,
        BigDecimal price,
        String currency,
        int billingPeriodDays,
        boolean active,
        int sortOrder
) {
    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(), plan.getName(), plan.getPrice(), plan.getCurrency(),
                plan.getBillingPeriodDays(), plan.isActive(), plan.getSortOrder()
        );
    }
}
