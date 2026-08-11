package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

public record AutoRenewRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
