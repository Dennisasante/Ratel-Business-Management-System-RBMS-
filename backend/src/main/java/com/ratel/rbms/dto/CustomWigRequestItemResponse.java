package com.ratel.rbms.dto;

import com.ratel.rbms.entity.CustomWigRequestItem;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomWigRequestItemResponse(
        UUID id,
        String description,
        BigDecimal price
) {
    public static CustomWigRequestItemResponse from(CustomWigRequestItem item) {
        return new CustomWigRequestItemResponse(item.getId(), item.getDescription(), item.getPrice());
    }
}
