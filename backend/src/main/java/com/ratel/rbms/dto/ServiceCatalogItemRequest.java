package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceCatalogItemRequest(
        @NotNull(message = "Service type is required")
        UUID serviceTypeId,

        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Price is required")
        BigDecimal price,

        // Every field below is nullable and defaults sensibly when omitted, so
        // existing callers (and the create form before it's updated) keep working.
        Boolean bookableOnline,

        @jakarta.validation.constraints.Min(value = 5, message = "Duration must be at least 5 minutes")
        Integer durationMinutes,

        @jakarta.validation.constraints.Min(value = 1, message = "Must allow at least 1 booking at a time")
        Integer maxConcurrentBookings,

        // e.g. home-service or bridal work — customer must supply where to go.
        Boolean requiresLocation,

        // "NONE"/"DEPOSIT"/"FULL" to override the business's default payment
        // policy for just this service, or null/blank to use the default.
        String paymentPolicyOverride
) {
}
