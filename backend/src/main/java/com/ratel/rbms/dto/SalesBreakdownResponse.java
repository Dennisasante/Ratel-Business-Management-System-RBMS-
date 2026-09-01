package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sales revenue split by one dimension (payment method, product category, or
 * salesperson — see DashboardService.SalesBreakdownDimension). Deliberately
 * no "by branch" option — RBMS has no branch/location concept.
 */
public record SalesBreakdownResponse(
        String dimension,
        List<Entry> entries
) {
    public record Entry(String label, BigDecimal revenue, BigDecimal percentOfTotal) {
    }
}
