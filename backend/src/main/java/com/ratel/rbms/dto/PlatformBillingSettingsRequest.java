package com.ratel.rbms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record PlatformBillingSettingsRequest(
        @Min(value = 0, message = "Trial length can't be negative")
        int trialDays,

        // Null hides the GHS/USD display toggle entirely — that's a valid,
        // intentional state, not a validation failure.
        @DecimalMin(value = "0", inclusive = false, message = "Exchange rate must be positive")
        BigDecimal usdDisplayRate,

        // Both null = leave unchanged (same convention as BusinessIntegrationsRequest).
        // Secret is write-only — see PlatformBillingSettingsResponse.paystackSecretConfigured.
        String paystackPublicKey,
        String paystackSecretKey
) {
}
