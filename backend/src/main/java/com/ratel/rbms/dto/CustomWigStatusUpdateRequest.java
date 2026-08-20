package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomWigStatusUpdateRequest(
        @NotBlank(message = "Status is required")
        String status
) {
}
