package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SmsPurchaseCheckoutRequest(
        @NotNull(message = "Choose a bundle")
        UUID bundleId
) {
}
