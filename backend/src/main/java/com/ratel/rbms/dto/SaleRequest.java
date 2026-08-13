package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaleRequest(
        // Required — every sale must be tied to a real Customer. "Walk-in" is
        // now just a Customer.source value, not a way to skip picking one.
        @NotNull(message = "A customer is required")
        UUID customerId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "A sale needs at least one item")
        @Valid
        List<SaleItemRequest> items
) {
}
