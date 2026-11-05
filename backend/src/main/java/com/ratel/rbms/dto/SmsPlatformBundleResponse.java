package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SmsBundle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** A bundle as the Super Admin sees it: the same as the business view, plus what Tallia earns on it. */
public record SmsPlatformBundleResponse(
        UUID id,
        String name,
        int credits,
        BigDecimal price,
        boolean active,
        int sortOrder,
        BigDecimal pricePerCredit,
        // Null until the provider cost per credit has been entered.
        BigDecimal profitPerCredit,
        BigDecimal profitPerBundle
) {
    public static SmsPlatformBundleResponse from(SmsBundle b, BigDecimal providerCostPerCredit) {
        BigDecimal perCredit = b.getPrice().divide(BigDecimal.valueOf(b.getCredits()), 4, RoundingMode.HALF_UP);
        BigDecimal profitPerCredit = providerCostPerCredit == null ? null : perCredit.subtract(providerCostPerCredit);
        BigDecimal profitPerBundle = providerCostPerCredit == null ? null
                : b.getPrice().subtract(providerCostPerCredit.multiply(BigDecimal.valueOf(b.getCredits()))).setScale(2, RoundingMode.HALF_UP);
        return new SmsPlatformBundleResponse(b.getId(), b.getName(), b.getCredits(), b.getPrice(), b.isActive(),
                b.getSortOrder(), perCredit, profitPerCredit, profitPerBundle);
    }
}
