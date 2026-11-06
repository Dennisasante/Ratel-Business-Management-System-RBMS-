package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicLoyaltyLookupRequest(
        @NotBlank(message = "Enter the number you gave at the shop")
        @Size(max = 30, message = "That number is too long")
        String phone
) {
}
