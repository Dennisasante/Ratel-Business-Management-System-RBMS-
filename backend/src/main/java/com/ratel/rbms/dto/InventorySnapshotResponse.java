package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Current-state inventory health — never date-filtered. */
public record InventorySnapshotResponse(
        int totalProducts,
        int totalQuantity,
        int lowStockCount,
        int outOfStockCount,
        // Sum of quantity * cost price across every active product — what the
        // business currently has tied up in stock, at cost (never selling price).
        BigDecimal inventoryValue,
        List<LowStockItem> lowStockItems
) {
    public record LowStockItem(UUID productId, String productName, int quantity, int lowStockThreshold) {
    }
}
