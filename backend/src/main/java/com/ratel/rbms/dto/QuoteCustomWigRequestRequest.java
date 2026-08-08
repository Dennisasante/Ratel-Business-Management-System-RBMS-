package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QuoteCustomWigRequestRequest(
        @NotNull(message = "A final price is required")
        @DecimalMin(value = "0", message = "Price can't be negative")
        BigDecimal finalPrice,

        String message
) {
}
