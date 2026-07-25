package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank(message = "Supplier name is required")
        String name,

        String phone,

        String email,

        String notes
) {
}
