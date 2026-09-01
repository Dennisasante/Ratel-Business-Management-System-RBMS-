package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Time-series data for the Sales & Profit chart. Granularity is picked
 * server-side based on the requested range's length (see DashboardService)
 * so a 7-day range plots daily points and a 90-day range doesn't plot 90
 * cramped bars. All three series are always returned together — the
 * frontend switches which one it plots without a second request.
 */
public record DashboardChartResponse(
        String granularity, // "DAY", "WEEK", or "MONTH"
        List<Point> points
) {
    public record Point(
            LocalDate bucketStart,
            String label,
            BigDecimal revenue,
            BigDecimal grossProfit,
            int orders
    ) {
    }
}
