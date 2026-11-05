package com.ratel.rbms.dto;

import java.util.UUID;

public record SmsPlatformAccountResponse(
        UUID businessId,
        String businessName,
        String senderId,
        String senderStatus,
        int creditBalance,
        // What mNotify said when the sender name was last checked; null if not checked just now.
        String note
) {
}
