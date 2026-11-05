package com.ratel.rbms.dto;

import com.ratel.rbms.entity.SmsCreditLedger;

import java.time.Instant;

public record SmsLedgerResponse(
        int delta,
        int balanceAfter,
        // PURCHASE / SEND / REFUND / ADJUSTMENT
        String type,
        String description,
        Instant createdAt
) {
    public static SmsLedgerResponse from(SmsCreditLedger l) {
        return new SmsLedgerResponse(l.getDelta(), l.getBalanceAfter(), l.getType(), l.getDescription(), l.getCreatedAt());
    }
}
