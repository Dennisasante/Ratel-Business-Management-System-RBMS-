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
        Map<String, Integer> statusCounts,
        double avgTurnaroundHours
) {
    public record TypeRevenue(UUID serviceTypeId, String serviceTypeName, BigDecimal revenue) {
    }
}
