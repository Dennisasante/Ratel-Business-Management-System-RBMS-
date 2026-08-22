package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ImportConfirmRequest(
        @NotEmpty(message = "No rows to import")
        List<ImportRow> rows
) {
}
