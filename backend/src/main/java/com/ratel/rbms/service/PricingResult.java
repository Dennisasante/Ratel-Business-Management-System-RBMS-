package com.ratel.rbms.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Talia Unified Platform, Phase 5B — the deterministic output of
 * {@link PackagePricingService#calculate}. {@code finalPrice} is {@code null} whenever the
 * calculation is invalid OR {@code manualQuoteRequired} — an invalid or quote-pending
 * configuration never produces a chargeable number. {@code basePrice}/{@code adjustments} are
 * still populated even when invalid, for diagnostic/display purposes (mirrors
 * {@code GateResult}'s own "reasons present even when blocked" shape from Phase 4).
 */
public record PricingResult(
        boolean valid,
        List<String> reasons,
        BigDecimal basePrice,
        List<PricingAdjustment> adjustments,
        BigDecimal finalPrice,
        boolean manualQuoteRequired
) {
}
