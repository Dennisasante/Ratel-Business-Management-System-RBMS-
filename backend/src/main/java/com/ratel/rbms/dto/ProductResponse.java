package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String category,
        UUID categoryId,
        String sku,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        int quantity,
        int lowStockThreshold,
        boolean lowStock,
        String supplierName,
        String imageUrl,
        boolean active,
        Instant createdAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getCategory(),
                p.getCategoryId(),
                p.getSku(),
                p.getCostPrice(),
                p.getSellingPrice(),
                p.getQuantity(),
                p.getLowStockThreshold(),
                p.getQuantity() <= p.getLowStockThreshold(),
                p.getSupplierName(),
                p.getImageUrl(),
                p.isActive(),
                p.getCreatedAt()
        );
    }
}
