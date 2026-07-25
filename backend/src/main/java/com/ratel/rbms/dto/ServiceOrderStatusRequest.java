package com.ratel.rbms.dto;

import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import jakarta.validation.constraints.NotNull;

public record ServiceOrderStatusRequest(
        @NotNull(message = "Status is required")
        ServiceOrderStatus status
) {
}
