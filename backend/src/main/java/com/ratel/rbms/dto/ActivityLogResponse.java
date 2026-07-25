package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ActivityLog;

import java.time.Instant;
import java.util.UUID;

public record ActivityLogResponse(
        UUID id,
        UUID businessId,
        String businessName,
        UUID userId,
        String userName,
        String action,
        String entityType,
        UUID entityId,
        Instant createdAt
) {
    public static ActivityLogResponse from(ActivityLog log, String businessName, String userName) {
        return new ActivityLogResponse(
                log.getId(),
                log.getBusinessId(),
                businessName,
                log.getUserId(),
                userName,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getCreatedAt()
        );
    }
}
