package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ServiceOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceOrderResponse(
        UUID id,
        Long orderNumber,
        UUID serviceTypeId,
        String serviceTypeName,
        String status,
        UUID customerId,
        String customerName,
        UUID serviceCatalogId,
        String serviceCatalogName,
        String notes,
        BigDecimal price,
        BigDecimal discountAmount,
        UUID assignedStaffId,
        String assignedStaffName,
        Instant receivedAt,
        Instant scheduledAt,
        Instant pickedUpAt,
        Instant readyEmailSentAt,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceOrderResponse from(
            ServiceOrder o,
            String serviceTypeName,
            String customerName,
            String serviceCatalogName,
            String assignedStaffName,
            String createdByName
    ) {
        return new ServiceOrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getServiceTypeId(),
                serviceTypeName,
                o.getStatus().name(),
                o.getCustomerId(),
                customerName,
                o.getServiceCatalogId(),
                serviceCatalogName,
                o.getNotes(),
                o.getPrice(),
                o.getDiscountAmount(),
                o.getAssignedStaffId(),
                assignedStaffName,
                o.getReceivedAt(),
                o.getScheduledAt(),
                o.getPickedUpAt(),
                o.getReadyEmailSentAt(),
                createdByName,
                o.getCreatedAt(),
                o.getUpdatedAt()
        );
    }
}
