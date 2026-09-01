package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Product;
import com.ratel.rbms.util.ProfitCalculator;

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
        // Computed, never entered by the user — see ProfitCalculator.
        // No markup field exists anywhere; margin/profit are always derived
        // from cost + selling price.
        BigDecimal profitPerUnit,
        BigDecimal profitMarginPercent,
        int quantity,
        int lowStockThreshold,
        boolean lowStock,
        String supplierName,
        String imageUrl,
        boolean active,
        boolean publishToWebsite,
        boolean syncedToWebsite,
        Instant createdAt
) {
    public static ProductResponse from(Product p) {
        BigDecimal profitPerUnit = ProfitCalculator.profit(p.getSellingPrice(), p.getCostPrice());
        BigDecimal profitMarginPercent = ProfitCalculator.marginPercent(profitPerUnit, p.getSellingPrice());

        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getCategory(),
                p.getCategoryId(),
                p.getSku(),
                p.getCostPrice(),
                p.getSellingPrice(),
                profitPerUnit,
                profitMarginPercent,
                p.getQuantity(),
                p.getLowStockThreshold(),
                p.getQuantity() <= p.getLowStockThreshold(),
                p.getSupplierName(),
                p.getImageUrl(),
                p.isActive(),
                p.isPublishToWebsite(),
                p.getWooProductId() != null,
                p.getCreatedAt()
        );
    }
}
