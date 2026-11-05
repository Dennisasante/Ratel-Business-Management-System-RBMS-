package com.ratel.rbms.dto;

public record SmsPurchaseVerifyResponse(
        boolean success,
        // The business's balance after this check — null when the payment
        // hasn't (yet) succeeded.
        Integer creditBalance,
        String message
) {
}
