package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record SmsPlatformSettingsRequest(
        // Null clears it.
        @DecimalMin(value = "0", message = "The cost can't be negative")
        BigDecimal providerCostPerCredit
) {
}
