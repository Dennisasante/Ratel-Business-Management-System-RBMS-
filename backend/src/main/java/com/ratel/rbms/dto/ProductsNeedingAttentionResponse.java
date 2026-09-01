package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Current-state product health lists for the "Products Needing Attention"
 * dashboard section — never date-filtered. A product can legitimately
 * appear in more than one list (e.g. out of stock AND below the margin
 * threshold).
 */
public record ProductsNeedingAttentionResponse(
        List<Item> lowStock,
        List<Item> outOfStock,
        List<Item> lowMargin
) {
    public record Item(
            UUID productId,
            String productName,
            int quantity,
            int lowStockThreshold,
            // Null for lowStock/outOfStock entries — only populated for lowMargin.
            BigDecimal profitMarginPercent
    ) {
    }
}
