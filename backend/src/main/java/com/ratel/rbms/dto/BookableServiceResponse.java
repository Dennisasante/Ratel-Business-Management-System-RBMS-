package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BookableServiceResponse(
        UUID serviceCatalogId,
        String serviceName,
        String serviceTypeName,
        BigDecimal price
) {
}
