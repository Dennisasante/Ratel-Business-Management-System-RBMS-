package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateEcommerceOrderStatusRequest(
        @NotBlank(message = "Status is required")
        String status
) {
}
