package com.ratel.rbms.dto;

import java.math.BigDecimal;

public record CustomWigRequestCreatedResponse(
        long requestNumber,
        String message,
        BigDecimal estimatedPrice
) {
}
