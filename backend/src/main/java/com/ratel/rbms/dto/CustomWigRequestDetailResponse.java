package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomWigRequestDetailResponse(
        UUID id,
        long requestNumber,
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        List<CustomWigSelectionResponse> selections,
        BigDecimal estimatedPrice,
        String inspirationPhotoUrl,
        String notes,
        String status,
        BigDecimal finalPrice,
        String ownerMessage,
        String whatsappLink,
        Instant createdAt
) {
}
