package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlatformStatsResponse(
        int totalBusinesses,
        int activeBusinesses,
        int totalUsers,
        BigDecimal totalPlatformRevenue,
        BigDecimal totalSubscriptionRevenue,
        List<DayCount> signupsByDay,
        List<DayCount> activityByDay,
        List<PlatformBillingStatusCount> billingStatusBreakdown,
        List<PlatformPlanMixEntry> planMix,
        long totalBookings,
        long totalEcommerceOrders,
        long totalCustomWigRequests,
        long totalServiceOrders
) {
}
