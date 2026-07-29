package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPaymentRequest(
        @NotBlank(message = "A payment reference is required")
        String reference
) {
}
