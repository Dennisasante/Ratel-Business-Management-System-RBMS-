package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.Industry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BusinessUpdateRequest(
        @NotBlank(message = "Business name is required")
        String name,

        @NotNull(message = "Industry is required")
        Industry industry,

        String location,

        String contactEmail,

        String contactPhone,

        String taxId,

        String defaultTermsAndConditions
) {
}
