package com.ratel.rbms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServicePackageRequest(
        @NotNull(message = "Service type is required")
        UUID serviceTypeId,

        @NotBlank(message = "Name is required")
        String name,

        String description,

        @NotNull(message = "Price is required")
        BigDecimal price,

        Boolean bookableOnline,

        @jakarta.validation.constraints.Min(value = 5, message = "Duration must be at least 5 minutes")
        Integer durationMinutes,

        @jakarta.validation.constraints.Min(value = 1, message = "Must allow at least 1 booking at a time")
        Integer maxConcurrentBookings,

        // Full replacement — saving a package rewrites its whole component list,
        // same pattern as CustomItemAttribute's options.
        @NotEmpty(message = "Add at least one item to the package")
        List<@Valid ServicePackageItemRequest> items
) {
}
