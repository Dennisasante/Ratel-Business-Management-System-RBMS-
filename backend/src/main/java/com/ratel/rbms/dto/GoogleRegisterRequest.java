package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.Industry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoogleRegisterRequest(
        @NotBlank(message = "Missing Google credential")
        String idToken,

        @NotBlank(message = "Business name is required")
        String businessName,

        @NotNull(message = "Industry is required")
        Industry industry,

        String location,

        String contactPhone
) {
}
