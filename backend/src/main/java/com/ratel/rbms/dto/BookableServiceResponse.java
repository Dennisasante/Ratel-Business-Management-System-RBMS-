package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BookableServiceResponse(
        UUID serviceCatalogId,
        UUID packageId,
        String serviceName,
        String serviceTypeName,
        String description,
        BigDecimal price,
        boolean isPackage,
        boolean requiresLocation,
        java.util.List<String> includedItems
) {
}
