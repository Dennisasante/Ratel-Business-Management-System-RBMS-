package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutRequest(
        @NotNull(message = "A plan must be selected")
        UUID planId
) {
}
