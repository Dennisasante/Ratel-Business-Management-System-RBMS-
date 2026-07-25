package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

// Shared toggle body for archive/restore-style endpoints (service catalog items today).
public record SetActiveRequest(
        @NotNull(message = "active is required")
        Boolean active
) {
}
