package com.ratel.rbms.dto;

import java.math.BigDecimal;

public record UpdateFinalPriceRequest(
        BigDecimal finalPrice,
        String note
) {
}
