package com.ratel.rbms.dto;

import com.ratel.rbms.entity.InvoiceItem;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemResponse(
        UUID id,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal subtotal
) {
    public static InvoiceItemResponse from(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountAmount(),
                item.getSubtotal()
        );
    }
}
