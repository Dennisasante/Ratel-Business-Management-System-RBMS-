package com.ratel.rbms.dto;

import com.ratel.rbms.entity.PurchaseOrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID productId,
        String productName,
        BigDecimal unitCost,
        int quantity,
        BigDecimal subtotal
) {
    public static PurchaseOrderItemResponse from(PurchaseOrderItem item) {
        return new PurchaseOrderItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getUnitCost(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
