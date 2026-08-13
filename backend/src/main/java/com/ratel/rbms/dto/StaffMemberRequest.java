package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record StaffMemberRequest(
        @NotBlank(message = "Name is required")
        String fullName,

        String phone,

        String notes
) {
}
