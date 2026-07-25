package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Edits notes/price/discount/assignee/scheduling only — type, customer and status
// change through their own flows.
public record ServiceOrderUpdateRequest(
        String notes,

        @NotNull(message = "Price is required")
        BigDecimal price,

        @DecimalMin(value = "0", message = "Discount can't be negative")
        BigDecimal discountAmount,

        // Nullable — clears the assignee when omitted/null.
        UUID assignedStaffId,

        // Nullable — clears the scheduled slot when omitted/null.
        Instant scheduledAt
) {
}
