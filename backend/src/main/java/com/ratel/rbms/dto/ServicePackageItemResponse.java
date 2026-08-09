package com.ratel.rbms.dto;

import java.util.UUID;

public record ServicePackageItemResponse(
        UUID id,
        UUID serviceCatalogId,
        String serviceCatalogName,
        int quantity
) {
}
