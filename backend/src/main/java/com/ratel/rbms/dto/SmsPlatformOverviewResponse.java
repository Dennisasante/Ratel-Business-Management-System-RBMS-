package com.ratel.rbms.dto;

import java.math.BigDecimal;

public record SmsPlatformOverviewResponse(
        // "mnotify" or "mock"
        String provider,
        boolean providerConfigured,
        // Tallia's own balance with the provider; null when it couldn't be read.
        BigDecimal providerBalance,
        BigDecimal providerBonus,
        // Sum of every business's unspent credits — what Tallia owes in sends.
        // If this ever exceeds providerBalance, a business could buy credits
        // Tallia can't actually deliver, so the page flags it.
        long outstandingClientCredits,
        // What the provider charges Tallia per credit (Super-Admin-entered); null if not entered yet.
        BigDecimal providerCostPerCredit,
        // What a business pays Tallia per credit (Super-Admin-entered); null while buying is closed.
        BigDecimal sellPricePerCredit,
        // Where the provider key in use comes from: SETTINGS (saved on this page), SERVER (.env) or NONE.
        String keySource,
        // The key in use with all but its last four characters hidden; null when there is none.
        String keyHint
) {
}
