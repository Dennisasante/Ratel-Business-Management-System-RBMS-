package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ServicePackageItemRequest(
        @NotNull(message = "Select a service")
        UUID serviceCatalogId,

        Integer quantity
) {
}
