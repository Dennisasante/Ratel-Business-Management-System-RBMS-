package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ServiceOrderReportResponse(
        LocalDate from,
        LocalDate to,
        List<TypeRevenue> revenueByType,
        // Finer-grained than revenueByType: one row per individual service
        // (catalog item, or the freeform serviceName snapshot for custom-priced
        // lines with no catalog item) instead of per broad category.
        List<ServiceRevenue> revenueByService,
        Map<String, Integer> statusCounts,
        double avgTurnaroundHours
) {
    public record TypeRevenue(UUID serviceTypeId, String serviceTypeName, BigDecimal revenue) {
    }

    public record ServiceRevenue(UUID serviceCatalogId, String serviceName, UUID serviceTypeId, String serviceTypeName, BigDecimal revenue) {
    }
}
