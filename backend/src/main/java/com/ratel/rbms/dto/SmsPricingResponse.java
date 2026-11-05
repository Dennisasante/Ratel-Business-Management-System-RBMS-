package com.ratel.rbms.dto;

import java.math.BigDecimal;

/** What a business sees when buying credits: the price of one credit and the allowed amounts. */
public record SmsPricingResponse(
        // Null until the Super Admin has set a selling price — buying is closed until then.
        BigDecimal pricePerCredit,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String currency
) {
}
