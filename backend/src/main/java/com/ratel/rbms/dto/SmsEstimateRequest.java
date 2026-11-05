package com.ratel.rbms.dto;

import jakarta.validation.constraints.Size;

public record SmsEstimateRequest(
        @Size(max = 1000, message = "That message is too long")
        String message
) {
}
