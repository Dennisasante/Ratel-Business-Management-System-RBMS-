package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SmsBundle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record SmsBundleResponse(
        UUID id,
        String name,
        int credits,
        BigDecimal price,
        boolean active,
        int sortOrder,
        // price / credits, 4dp — lets a business compare packs at a glance.
        BigDecimal pricePerCredit
) {
    public static SmsBundleResponse from(SmsBundle b) {
        return new SmsBundleResponse(
                b.getId(), b.getName(), b.getCredits(), b.getPrice(), b.isActive(), b.getSortOrder(),
                b.getPrice().divide(BigDecimal.valueOf(b.getCredits()), 4, RoundingMode.HALF_UP));
    }
}
