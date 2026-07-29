package com.ratel.rbms.dto;

public record CheckoutResponse(
        String accessCode,
        String reference
) {
}
