package com.ratel.rbms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExpenseEditRequest(
        @Valid
        @NotNull(message = "Expense details are required")
        ExpenseRequest expense,

        @NotBlank(message = "A reason is required")
        @Size(max = 240)
        String reason
) {
}
