package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOrderItemRequest(
        @NotNull(message = "Category is required")
        UUID serviceTypeId,

        // Nullable: a custom/freeform-priced line has no catalog item behind it.
        UUID serviceCatalogId,

        // Nullable: a free-text description for a line with no catalog item —
        // e.g. "6 inches HD bone straight wig, middle part" typed straight
        // from an Instagram/WhatsApp order. Takes precedence over the catalog/
        // category name when present. See ServiceOrderService.create().
        String customName,

        // Required: the frontend always resolves a concrete price client-side
        // before submit (from the catalog pick, or typed manually for a
        // custom line) — same "not falls back server-side" behavior the
        // single-service form already had.
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0", message = "Price can't be negative")
        BigDecimal price,

        // Nullable: how much was knocked off at the attendant's/owner's discretion,
        // recorded purely for transparency — defaults to none.
        @DecimalMin(value = "0", message = "Discount can't be negative")
        BigDecimal discountAmount
) {
}
