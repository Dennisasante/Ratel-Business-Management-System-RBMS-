package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitOtpRequest(
        @NotBlank String reference,
        @NotBlank String otp
) {
}
