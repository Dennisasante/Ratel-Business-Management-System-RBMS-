package com.ratel.rbms.dto;

import java.math.BigDecimal;

public record PlatformPlanMixEntry(
        String planName,
        long businessCount,
        BigDecimal mrr
) {
}
