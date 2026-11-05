package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SmsSendTestRequest(
        @NotBlank(message = "A phone number is required")
        String phone,

        @NotBlank(message = "Write a message first")
        @Size(max = 1000, message = "That message is too long")
        String message
) {
}
