package com.ratel.rbms.dto;

import com.ratel.rbms.entity.EcommerceOrderItem;

import java.math.BigDecimal;

public record EcommerceOrderItemResponse(
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
    public static EcommerceOrderItemResponse from(EcommerceOrderItem item) {
        return new EcommerceOrderItemResponse(item.getProductName(), item.getQuantity(), item.getUnitPrice());
    }
}
