package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull(message = "An amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "A payment method is required")
        PaymentMethod method,

        String note
) {
}
