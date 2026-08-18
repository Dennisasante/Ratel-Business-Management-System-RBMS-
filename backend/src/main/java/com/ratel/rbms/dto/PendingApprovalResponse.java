package com.ratel.rbms.dto;

import com.ratel.rbms.entity.PendingApproval;

import java.time.Instant;
import java.util.UUID;

public record PendingApprovalResponse(
        UUID id,
        String sourceType,
        UUID sourceId,
        String actionType,
        String summary,
        String status,
        String requestedByName,
        Instant requestedAt
) {
    public static PendingApprovalResponse from(PendingApproval pa, String requestedByName) {
        return new PendingApprovalResponse(
                pa.getId(), pa.getSourceType().name(), pa.getSourceId(), pa.getActionType().name(),
                pa.getSummary(), pa.getStatus().name(), requestedByName, pa.getRequestedAt()
        );
    }
}
