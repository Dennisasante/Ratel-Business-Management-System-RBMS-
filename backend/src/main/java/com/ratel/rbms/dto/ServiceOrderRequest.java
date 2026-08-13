package com.ratel.rbms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceOrderRequest(
        // Required — every order must be tied to a real Customer. "Walk-in" is
        // now just a Customer.source value, not a way to skip picking one.
        @NotNull(message = "A customer is required")
        UUID customerId,

        // At least one service — a customer bringing 2-3 different services
        // in one visit is one order with multiple items, not several orders.
        @NotEmpty(message = "At least one service is required")
        @Valid
        List<ServiceOrderItemRequest> items,

        String notes,

        // Nullable: tracking only, can be assigned later.
        UUID assignedStaffId,

        // Nullable: set only when this is booked ahead for a future slot.
        Instant scheduledAt
) {
}
