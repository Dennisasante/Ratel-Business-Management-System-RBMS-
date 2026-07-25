package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal revenue,
        BigDecimal expenses,
        BigDecimal profit,
        int salesCount,
        int expenseCount
) {
}
