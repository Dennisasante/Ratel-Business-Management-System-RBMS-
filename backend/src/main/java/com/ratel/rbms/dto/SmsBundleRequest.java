package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SmsBundleRequest(
        @NotBlank(message = "A bundle name is required")
        @Size(max = 80, message = "Keep the name under 80 characters")
        String name,

        @NotNull(message = "Credits are required")
        @Min(value = 1, message = "A bundle needs at least 1 credit")
        Integer credits,

        @NotNull(message = "A price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price,

        Boolean active,
        Integer sortOrder
) {
}
