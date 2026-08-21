package com.ratel.rbms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceRequest(
        // Nullable — a client who isn't in the Customer list yet is covered
        // by the four snapshot fields below instead.
        UUID customerId,

        String customerName,
        String customerEmail,
        String customerPhone,
        String customerAddress,

        @NotNull(message = "Issue date is required")
        LocalDate issueDate,

        // Nullable — not every invoice has a due date.
        LocalDate dueDate,

        String notes,

        @NotEmpty(message = "Add at least one line item")
        @Valid
        List<InvoiceItemRequest> items
) {
}
