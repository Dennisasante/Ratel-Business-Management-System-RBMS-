package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BookableServiceResponse(
        UUID serviceCatalogId,
        UUID packageId,
        String serviceName,
        UUID serviceTypeId,
        String serviceTypeName,
        String description,
        BigDecimal price,
        boolean isPackage,
        boolean requiresLocation,
        java.util.List<String> includedItems,
        // Effective NONE/DEPOSIT/FULL for this specific service/package —
        // already resolves any per-item override against the business default.
        String paymentPolicy
) {
}
