package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.Industry;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterBusinessRequest(

        @NotBlank(message = "Business name is required")
        @Size(max = 150)
        String businessName,

        @NotNull(message = "Industry is required")
        Industry industry,

        String location,

        String contactPhone,

        @NotBlank(message = "Owner's full name is required")
        String ownerFullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
}
