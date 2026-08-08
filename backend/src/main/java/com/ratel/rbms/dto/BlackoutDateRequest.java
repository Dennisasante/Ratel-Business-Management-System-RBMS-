package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BlackoutDateRequest(
        @NotNull(message = "Date is required")
        LocalDate date,

        String label
) {
}
