package com.ratel.rbms.controller;

import com.ratel.rbms.dto.DashboardAttentionResponse;
import com.ratel.rbms.dto.DashboardChartResponse;
import com.ratel.rbms.dto.DashboardSummaryResponse;
import com.ratel.rbms.dto.InventorySnapshotResponse;
import com.ratel.rbms.dto.ProductsNeedingAttentionResponse;
import com.ratel.rbms.dto.SalesBreakdownResponse;
import com.ratel.rbms.dto.TopProductResponse;
import com.ratel.rbms.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Same audience as ReportController (everyone except STAFF, who get their
 * own narrow "your service orders" view on the frontend instead) — the
 * figures here are business-wide financials, not something a STAFF account
 * should see.
 *
 * Every endpoint takes optional from/to (both null = all time, matching the
 * shared frontend DateRangeFilter's "All" preset) — see DashboardService for
 * exactly what "all time" resolves to and when period-over-period
 * comparisons are skipped as a result.
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return dashboardService.summary(from, to);
    }

    @GetMapping("/chart")
    public DashboardChartResponse chart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return dashboardService.chart(from, to);
    }

    @GetMapping("/attention")
    public DashboardAttentionResponse attention() {
        return dashboardService.attention();
    }

    @GetMapping("/top-products")
    public List<TopProductResponse> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "REVENUE") DashboardService.TopProductRankMetric rankBy,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        return dashboardService.topProducts(from, to, rankBy, limit);
    }

    @GetMapping("/products-needing-attention")
    public ProductsNeedingAttentionResponse productsNeedingAttention() {
        return dashboardService.productsNeedingAttention();
    }

    @GetMapping("/inventory-snapshot")
    public InventorySnapshotResponse inventorySnapshot() {
        return dashboardService.inventorySnapshot();
    }

    @GetMapping("/sales-breakdown")
    public SalesBreakdownResponse salesBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "PAYMENT_METHOD") DashboardService.SalesBreakdownDimension by
    ) {
        return dashboardService.salesBreakdown(from, to, by);
    }
}
