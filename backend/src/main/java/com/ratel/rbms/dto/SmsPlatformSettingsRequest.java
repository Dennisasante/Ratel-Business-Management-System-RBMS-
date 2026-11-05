package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record SmsPlatformSettingsRequest(
        // Null clears it.
        @DecimalMin(value = "0", message = "The cost can't be negative")
        BigDecimal providerCostPerCredit,
        // What a business pays per credit. Null closes buying until it is set again.
        @DecimalMin(value = "0.0001", message = "The selling price must be more than zero")
        BigDecimal sellPricePerCredit
) {
}
