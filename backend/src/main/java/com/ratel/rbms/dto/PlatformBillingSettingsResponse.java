package com.ratel.rbms.dto;

import com.ratel.rbms.entity.PlatformBillingSettings;

import java.math.BigDecimal;

public record PlatformBillingSettingsResponse(
        int trialDays,
        BigDecimal usdDisplayRate
) {
    public static PlatformBillingSettingsResponse from(PlatformBillingSettings settings) {
        return new PlatformBillingSettingsResponse(settings.getTrialDays(), settings.getUsdDisplayRate());
    }
}
