package com.ratel.rbms.dto;

import com.ratel.rbms.entity.CustomItemAttributeOption;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomItemAttributeOptionResponse(
        UUID id,
        String label,
        BigDecimal priceModifier,
        int sortOrder,
        boolean requiresManualQuote
) {
    public static CustomItemAttributeOptionResponse from(CustomItemAttributeOption o) {
        return new CustomItemAttributeOptionResponse(o.getId(), o.getLabel(), o.getPriceModifier(), o.getSortOrder(), o.isRequiresManualQuote());
    }
}
