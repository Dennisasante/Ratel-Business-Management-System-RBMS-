package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of the Top Products table, for whichever ranking metric the
 * caller asked for (see DashboardService.TopProductRankMetric). All figures
 * come from completed sale line items in the requested range — see
 * DashboardService for exactly which sales/items are counted. grossProfit/
 * grossMarginPercent are null (never a fabricated 0) for a product whose
 * historical cost is unknown for every one of its sold units in range.
 */
public record TopProductResponse(
        UUID productId,
        String productName,
        int unitsSold,
        BigDecimal revenue,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercent
) {
}
