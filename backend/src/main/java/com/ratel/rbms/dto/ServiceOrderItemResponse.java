package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ServiceOrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceOrderItemResponse(
        UUID id,
        UUID serviceTypeId,
        String serviceTypeName,
        UUID serviceCatalogId,
        String serviceName,
        BigDecimal price,
        BigDecimal discountAmount
) {
    public static ServiceOrderItemResponse from(ServiceOrderItem item, String serviceTypeName) {
        return new ServiceOrderItemResponse(
                item.getId(),
                item.getServiceTypeId(),
                serviceTypeName,
                item.getServiceCatalogId(),
                item.getServiceName(),
                item.getPrice(),
                item.getDiscountAmount()
        );
    }
}
