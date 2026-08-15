package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String category,
        String description,
        String paymentMethod,
        BigDecimal amount,
        LocalDate expenseDate,
        String recordedByName,
        Instant createdAt
) {
    public static ExpenseResponse from(Expense e, String recordedByName) {
        return new ExpenseResponse(
                e.getId(),
                e.getCategory().name(),
                e.getDescription(),
                e.getPaymentMethod().name(),
                e.getAmount(),
                e.getExpenseDate(),
                recordedByName,
                e.getCreatedAt()
        );
    }
}
