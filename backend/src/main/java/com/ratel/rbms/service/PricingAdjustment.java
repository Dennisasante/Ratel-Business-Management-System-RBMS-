package com.ratel.rbms.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 5B — one component's price contribution within a
 * {@link PricingResult}. Exactly one of {@code optionId} (SELECTION components) or
 * {@code quantity} (QUANTITY components) is populated, matching which kind of
 * {@code PackageComponent} produced it.
 */
public record PricingAdjustment(
        UUID componentId,
        UUID optionId,
        Integer quantity,
        BigDecimal amount,
        boolean viaSubstitution
) {
}
