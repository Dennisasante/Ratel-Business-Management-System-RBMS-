package com.ratel.rbms.dto;

/**
 * "Needs your attention" counts. Every field is a current-state count, never
 * affected by the dashboard's date filter (e.g. "currently out of stock" has
 * no meaningful date range). custom-wig/e-commerce counts are 0 when the
 * business's plan doesn't include that module — the frontend should hide
 * that row entirely rather than show a permanent 0, same as it already does
 * today by checking business.planFeatures.
 */
public record DashboardAttentionResponse(
        int lowStockProducts,
        int outOfStockProducts,
        int lowMarginProducts,
        boolean customWigRequestsEnabled,
        int newCustomWigRequests,
        boolean ecommerceEnabled,
        int ecommerceOrdersToFulfill,
        int pendingApprovals,
        int overdueInvoices
) {
}
