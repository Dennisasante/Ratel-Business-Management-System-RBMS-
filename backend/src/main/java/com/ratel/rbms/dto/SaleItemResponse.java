package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SaleItem;
import com.ratel.rbms.entity.enums.SaleItemType;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemResponse(
        UUID id,
        SaleItemType itemType,
        UUID productId,
        UUID serviceCatalogId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        boolean gift
) {
    public static SaleItemResponse from(SaleItem item) {
        return new SaleItemResponse(
                item.getId(),
                item.getItemType(),
                item.getProductId(),
                item.getServiceCatalogId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getDiscountAmount(),
                item.getSubtotal(),
                item.isGift()
        );
    }
}
