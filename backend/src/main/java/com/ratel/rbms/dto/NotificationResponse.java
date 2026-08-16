package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String sourceType,
        UUID sourceId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(),
                n.getSourceType(), n.getSourceId(), n.isRead(), n.getCreatedAt()
        );
    }
}
