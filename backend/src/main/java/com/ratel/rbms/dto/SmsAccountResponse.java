package com.ratel.rbms.dto;

public record SmsAccountResponse(
        String senderId,
        // NONE / PENDING / APPROVED — a business can only send once APPROVED.
        String senderStatus,
        boolean senderApproved,
        int creditBalance,
        int lowBalanceThreshold,
        boolean lowBalance,
        // False when Tallia itself has no SMS provider credentials configured —
        // the page says SMS is unavailable instead of letting a send fail.
        boolean providerConfigured,
        // PAPER / SMS / BOTH — how customers get their receipt.
        String receiptMode
) {
}
