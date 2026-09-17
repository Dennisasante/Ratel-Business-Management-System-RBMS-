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
        String description,
        // Non-empty ONLY for a staff-logged multi-item order (see V62's own
        // comment) — empty for every public-widget submission and every
        // pre-existing single-item staff request, which keep using
        // description/estimatedPrice above exactly as before.
        List<CustomWigRequestItemResponse> items,
        BigDecimal estimatedPrice,
        String inspirationPhotoUrl,
        String notes,
        String status,
        BigDecimal finalPrice,
        String ownerMessage,
        String paymentStatus,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        String paymentMethod,
        String whatsappLink,
        String source,
        Instant createdAt
) {
}
