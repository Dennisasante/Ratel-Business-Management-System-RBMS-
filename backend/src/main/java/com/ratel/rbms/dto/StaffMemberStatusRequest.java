package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

public record StaffMemberStatusRequest(
        @NotNull(message = "active is required")
        Boolean active
) {
}
