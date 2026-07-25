package com.ratel.rbms.dto;

import com.ratel.rbms.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        Long poNumber,
        UUID supplierId,
        String supplierName,
        String status,
        BigDecimal totalAmount,
        String createdByName,
        List<PurchaseOrderItemResponse> items,
        Instant createdAt,
        Instant receivedAt
) {
    public static PurchaseOrderResponse from(
            PurchaseOrder po, String supplierName, String createdByName, List<PurchaseOrderItemResponse> items
    ) {
        return new PurchaseOrderResponse(
                po.getId(),
                po.getPoNumber(),
                po.getSupplierId(),
                supplierName,
                po.getStatus().name(),
                po.getTotalAmount(),
                createdByName,
                items,
                po.getCreatedAt(),
                po.getReceivedAt()
        );
    }
}
