package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceCatalogItemRequest(
        @NotNull(message = "Service type is required")
        UUID serviceTypeId,

        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Price is required")
        BigDecimal price
) {
}
