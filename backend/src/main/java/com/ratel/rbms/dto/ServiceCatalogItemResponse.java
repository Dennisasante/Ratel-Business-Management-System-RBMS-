package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ServiceCatalogItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceCatalogItemResponse(
        UUID id,
        UUID serviceTypeId,
        String serviceTypeName,
        String name,
        BigDecimal price,
        boolean active,
        boolean bookableOnline,
        int durationMinutes,
        int maxConcurrentBookings,
        Instant createdAt
) {
    public static ServiceCatalogItemResponse from(ServiceCatalogItem item, String serviceTypeName) {
        return new ServiceCatalogItemResponse(
                item.getId(),
                item.getServiceTypeId(),
                serviceTypeName,
                item.getName(),
                item.getPrice(),
                item.isActive(),
                item.isBookableOnline(),
                item.getDurationMinutes(),
                item.getMaxConcurrentBookings(),
                item.getCreatedAt()
        );
    }
}
