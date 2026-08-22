package com.ratel.rbms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
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

        // Nullable — no tax charged at all is the common case. Applied over
        // (subtotal - discountAmount) when present.
        @DecimalMin(value = "0", message = "Tax rate can't be negative")
        BigDecimal taxRate,

        @DecimalMin(value = "0", message = "Shipping can't be negative")
        BigDecimal shippingAmount,

        @NotEmpty(message = "Add at least one line item")
        @Valid
        List<InvoiceItemRequest> items
) {
}
