package com.ratel.rbms.dto;

public record SmsReceiptStatusResponse(
        // OFF / UNPAID / NO_CUSTOMER / NO_PHONE / NOT_READY / NO_CREDITS / PENDING / SENT / FAILED
        String status,
        // Plain-language line for the cashier's screen.
        String detail,
        // True when a "Send SMS receipt" button would work right now.
        boolean canSend
) {
}
