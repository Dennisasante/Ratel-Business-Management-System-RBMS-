package com.ratel.rbms.dto;

import java.math.BigDecimal;

public record BookingCreatedResponse(
        String manageToken,
        long bookingNumber,
        String message,
        boolean paymentRequired,
        BigDecimal amountDue
) {
}
