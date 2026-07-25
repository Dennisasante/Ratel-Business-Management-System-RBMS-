package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceOrderRequest(
        @NotNull(message = "Service type is required")
        UUID serviceTypeId,

        // Nullable: wig-making/walk-in clients don't always have a customer record on file yet.
        UUID customerId,

        // Nullable: not every order prices from the fixed catalog.
        UUID serviceCatalogId,

        String notes,

        // Nullable: when a catalog item is picked, price pre-fills from it server-side.
        // When set explicitly, it overrides the catalog price for this order only.
        BigDecimal price,

        // Nullable: how much was knocked off at the attendant's/owner's discretion,
        // recorded purely for transparency — defaults to none.
        @DecimalMin(value = "0", message = "Discount can't be negative")
        BigDecimal discountAmount,

        // Nullable: tracking only, can be assigned later.
        UUID assignedStaffId,

        // Nullable: set only when this is booked ahead for a future slot.
        Instant scheduledAt
) {
}
