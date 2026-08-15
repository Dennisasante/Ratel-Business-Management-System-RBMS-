package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.ExpenseCategory;
import com.ratel.rbms.entity.enums.ExpensePaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRequest(
        @NotNull(message = "Category is required")
        ExpenseCategory category,

        String description,

        @NotNull(message = "Payment method is required")
        ExpensePaymentMethod paymentMethod,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0", message = "Amount can't be negative")
        BigDecimal amount,

        // Optional: defaults to today if omitted, so a quick "log this expense now" call doesn't need it.
        LocalDate expenseDate
) {
}
