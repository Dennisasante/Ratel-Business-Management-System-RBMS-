package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CustomItemAttributeOptionRequest(
        @NotBlank(message = "Option label is required")
        String label,

        BigDecimal priceModifier,

        Integer sortOrder
) {
}
