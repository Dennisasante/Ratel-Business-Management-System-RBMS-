package com.ratel.rbms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        // Nullable: not every restock has a formal supplier on file.
        UUID supplierId,

        @NotEmpty(message = "A purchase order needs at least one item")
        @Valid
        List<PurchaseOrderItemRequest> items
) {
}
