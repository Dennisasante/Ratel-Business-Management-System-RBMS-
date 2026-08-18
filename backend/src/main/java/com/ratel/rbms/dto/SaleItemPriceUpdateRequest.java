package com.ratel.rbms.dto;

import java.math.BigDecimal;

public record SaleItemPriceUpdateRequest(
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        String note
) {
}
