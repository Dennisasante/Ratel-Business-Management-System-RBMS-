package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemRequest(
        // Exactly one of productId/serviceCatalogId must be set — not
        // expressible via bean validation alone, checked in SaleService the
        // same way CreateBookingRequest's serviceCatalogId/packageId pair is.
        UUID productId,

        UUID serviceCatalogId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity,

        // Nullable: no discount is the common case. At the attendant's/owner's discretion.
        @DecimalMin(value = "0", message = "Discount can't be negative")
        BigDecimal discountAmount
) {
}
