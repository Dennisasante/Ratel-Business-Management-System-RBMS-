package com.ratel.rbms.dto;

public record SmsReceiptPreviewResponse(String message, int segments, int length, boolean unicode) {
}
