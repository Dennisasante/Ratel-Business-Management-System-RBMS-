package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        String name,

        // Deprecated free-text field, kept only until the old column is dropped in V9.
        String category,

        // Nullable: uncategorized is a valid state.
        UUID categoryId,

        String sku,

        @DecimalMin(value = "0", message = "Cost price can't be negative")
        BigDecimal costPrice,

        @DecimalMin(value = "0", message = "Selling price can't be negative")
        BigDecimal sellingPrice,

        @Min(value = 0, message = "Opening quantity can't be negative")
        Integer quantity,

        @Min(value = 0, message = "Low stock threshold can't be negative")
        Integer lowStockThreshold,

        String supplierName,

        String imageUrl
) {
}
