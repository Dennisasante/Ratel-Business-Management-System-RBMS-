package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MobileMoneyChargeRequest(
        @NotBlank(message = "A phone number is required")
        String phone,

        // mtn / atl (AirtelTigo) / vod (Vodafone/Telecel) — Paystack's Ghana mobile
        // money providers.
        @NotBlank(message = "Choose a mobile money provider")
        @Pattern(regexp = "mtn|atl|vod", message = "Unknown mobile money provider")
        String provider
) {
}
