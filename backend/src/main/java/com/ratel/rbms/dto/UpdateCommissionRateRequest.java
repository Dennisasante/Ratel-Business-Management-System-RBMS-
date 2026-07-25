package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateCommissionRateRequest(
        @NotNull(message = "Rate is required")
        @DecimalMin(value = "0", message = "Rate can't be negative")
        @DecimalMax(value = "100", message = "Rate can't exceed 100%")
        BigDecimal rate
) {
}
