package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SmsAmountCheckoutRequest(
        @NotNull(message = "Enter how much you want to spend")
        BigDecimal amount
) {
}
