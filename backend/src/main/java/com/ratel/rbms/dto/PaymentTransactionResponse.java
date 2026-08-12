package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID id,
        String direction,
        String sourceType,
        UUID sourceId,
        // A human-readable label for whatever sourceId points to, e.g. "Service
        // Order #38" — resolved at read time so the ledger reads like a business
        // record, not a table of raw UUIDs.
        String sourceLabel,
        String gateway,
        String method,
        BigDecimal amount,
        String currency,
        String status,
        String gatewayReference,
        UUID customerId,
        String customerName,
        String customerPhone,
        String note,
        String createdByName,
        Instant paidAt,
        Instant createdAt
) {
}
