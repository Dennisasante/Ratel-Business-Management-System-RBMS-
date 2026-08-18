package com.ratel.rbms.dto;

import java.util.UUID;

// Wraps SaleItemPriceUpdateRequest with the item id for approval-queue
// storage — sourceId on PendingApproval is the sale's own id (consistent
// with every other queued action type), so the item id has to travel
// inside the payload instead.
public record SaleItemPricePayload(
        UUID itemId,
        SaleItemPriceUpdateRequest request
) {
}
