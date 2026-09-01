package com.ratel.rbms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single formula for profit/margin everywhere it's computed in this
 * app — SaleItemResponse (actual per-line sale profit), ProductResponse
 * (list-price profit per unit), and DashboardService (aggregated gross
 * profit/margin). One shared function means the dashboard and every report
 * can never drift into two different formulas for the same number.
 *
 * Every method here is null-safe and never divides by zero — an unknown
 * cost (a SERVICE line, or a PRODUCT sold before cost tracking existed) or
 * a zero denominator both simply produce null, which callers render as
 * "—"/omit rather than a misleading 0% or a crash.
 */
public final class ProfitCalculator {

    private ProfitCalculator() {
    }

    /** Selling price minus cost, or null if either side is unknown. */
    public static BigDecimal profit(BigDecimal sellingPriceOrRevenue, BigDecimal costOrCogs) {
        if (sellingPriceOrRevenue == null || costOrCogs == null) return null;
        return sellingPriceOrRevenue.subtract(costOrCogs);
    }

    /**
     * (profit / denominator) * 100, rounded to 2dp — null if the profit is
     * unknown or the denominator is zero/negative (spec: "handle zero
     * revenue safely," never a division-by-zero).
     */
    public static BigDecimal marginPercent(BigDecimal profit, BigDecimal denominator) {
        if (profit == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) return null;
        return profit.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
