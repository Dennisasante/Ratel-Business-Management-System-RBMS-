package com.ratel.rbms.dto;

import java.util.List;
import java.util.UUID;

public record SmsCampaignDetailResponse(
        SmsCampaignSummaryResponse campaign,
        // The first 500 recipients — enough to read the delivery report; the counts above cover everyone.
        List<Recipient> recipients
) {
    public record Recipient(UUID customerId, String customerName, String phone, String status, String error) {
    }
}
