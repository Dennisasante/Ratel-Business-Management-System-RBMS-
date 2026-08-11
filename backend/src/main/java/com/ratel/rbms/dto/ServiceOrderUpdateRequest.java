package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Edits notes/price/discount/assignee/scheduling only — type, customer and status
// change through their own flows. price/discountAmount are nullable: omit both
// to leave them untouched, which is the only option for a multi-item order —
// ServiceOrderService rejects a price/discount change attempt when the order
// has more than one ServiceOrderItem (editing an individual line isn't
// supported yet — see ServiceOrderService.update()).
public record ServiceOrderUpdateRequest(
        String notes,

        BigDecimal price,

        @DecimalMin(value = "0", message = "Discount can't be negative")
        BigDecimal discountAmount,

        // Nullable — clears the assignee when omitted/null.
        UUID assignedStaffId,

        // Nullable — clears the scheduled slot when omitted/null.
        Instant scheduledAt
) {
}
