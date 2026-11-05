package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SmsCreditAdjustRequest(
        // Positive grants credits, negative removes them. Never zero.
        @NotNull(message = "Credits are required")
        Integer credits,

        @NotBlank(message = "A reason is required")
        @Size(max = 200, message = "Keep the reason under 200 characters")
        String reason
) {
}
