package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InvoiceItemRequest(
        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0", message = "Unit price can't be negative")
        BigDecimal unitPrice,

        // Nullable: no discount is the common case.
        @DecimalMin(value = "0", message = "Discount can't be negative")
        BigDecimal discountAmount
) {
}
