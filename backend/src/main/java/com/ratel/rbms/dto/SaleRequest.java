package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaleRequest(
        // Nullable: walk-in customers are common (the spec's own example sale
        // is just "Customer: Ama" but plenty of businesses won't record one).
        UUID customerId,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotEmpty(message = "A sale needs at least one item")
        @Valid
        List<SaleItemRequest> items
) {
}
