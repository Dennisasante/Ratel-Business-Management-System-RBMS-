package com.ratel.rbms.dto;

import com.ratel.rbms.entity.ServicePackage;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServicePackageResponse(
        UUID id,
        UUID serviceTypeId,
        String serviceTypeName,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        boolean bookableOnline,
        int durationMinutes,
        int maxConcurrentBookings,
        List<ServicePackageItemResponse> items
) {
    public static ServicePackageResponse from(ServicePackage pkg, String serviceTypeName, List<ServicePackageItemResponse> items) {
        return new ServicePackageResponse(
                pkg.getId(),
                pkg.getServiceTypeId(),
                serviceTypeName,
                pkg.getName(),
                pkg.getDescription(),
                pkg.getPrice(),
                pkg.isActive(),
                pkg.isBookableOnline(),
                pkg.getDurationMinutes(),
                pkg.getMaxConcurrentBookings(),
                items
        );
    }
}
