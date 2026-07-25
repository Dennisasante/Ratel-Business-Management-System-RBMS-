package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SaleItem;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal discountAmount,
        BigDecimal subtotal
) {
    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getDiscountAmount(),
                item.getSubtotal()
        );
    }
}
