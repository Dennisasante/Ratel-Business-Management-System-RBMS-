package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ServiceType;

import java.time.Instant;
import java.util.UUID;

public record ServiceTypeResponse(
        UUID id,
        String name,
        long usageCount,
        Instant createdAt
) {
    public static ServiceTypeResponse from(ServiceType type, long usageCount) {
        return new ServiceTypeResponse(type.getId(), type.getName(), usageCount, type.getCreatedAt());
    }
}
