package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SubscriptionPlanRequest(
        @NotBlank(message = "Plan name is required")
        String name,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0", inclusive = true, message = "Price can't be negative")
        BigDecimal price,

        @NotBlank(message = "Currency is required")
        String currency,

        @Min(value = 1, message = "Billing period must be at least 1 day")
        int billingPeriodDays,

        int sortOrder,

        // Feature codes this plan grants: BOOKING_WIDGET, WOOCOMMERCE_SYNC, CUSTOM_WIG_REQUESTS.
        List<String> features
) {
}
