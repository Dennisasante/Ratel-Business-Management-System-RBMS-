package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record ServiceTypeRequest(
        @NotBlank(message = "Service type name is required")
        String name
) {
}
