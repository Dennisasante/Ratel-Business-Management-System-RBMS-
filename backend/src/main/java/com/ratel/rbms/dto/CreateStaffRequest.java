package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStaffRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotNull(message = "Role is required")
        Role role,

        @NotBlank(message = "Temporary password is required")
        @Size(min = 8, message = "Temporary password must be at least 8 characters")
        String temporaryPassword
) {
}
