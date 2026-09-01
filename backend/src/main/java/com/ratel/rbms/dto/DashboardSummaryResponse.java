package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The Dashboard's "Business Overview" section. Revenue/COGS/grossProfit/
 * grossMarginPercent/expenses/netProfit are all scoped to [from, to] — see
 * DashboardService for exactly how each is computed (same source data as
 * ReportService, never a second formula). teamMembers/activeServiceOrders
 * are current-state counts, not affected by the date range at all.
 *
 * Every "previous*" field is the same metric for the immediately preceding
 * period of equal length, for a period-over-period comparison — null
 * whenever the caller requested an open-ended range (e.g. the "All" filter),
 * since there's no equivalent prior period to compare against. The frontend
 * should hide the comparison badge rather than show a misleading 0%/∞% when
 * these are null.
 */
public record DashboardSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal revenue,
        BigDecimal previousRevenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal previousGrossProfit,
        // Null (never 0) when revenue is zero/negative — see ProfitCalculator.
        BigDecimal grossMarginPercent,
        BigDecimal previousGrossMarginPercent,
        BigDecimal expenses,
        BigDecimal previousExpenses,
        BigDecimal netProfit,
        BigDecimal previousNetProfit,
        int teamMembers,
        int activeServiceOrders
) {
}
