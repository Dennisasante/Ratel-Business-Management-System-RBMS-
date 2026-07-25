package com.ratel.rbms.dto;

import com.ratel.rbms.entity.StockMovement;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        String movementType,
        int quantityChange,
        int resultingQuantity,
        String note,
        Instant createdAt
) {
    public static StockMovementResponse from(StockMovement m) {
        return new StockMovementResponse(
                m.getId(),
                m.getMovementType().name(),
                m.getQuantityChange(),
                m.getResultingQuantity(),
                m.getNote(),
                m.getCreatedAt()
        );
    }
}
