package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Restaurant-AI-demo phase — lets an AI tool (or any future caller) present a canonical
 * package's real substitution structure without ever touching PackageComponent/Option/
 * SubstitutionRule directly. Read-only; mirrors exactly what PackagePricingService itself
 * would accept as a valid {@code selections} entry for this offering.
 */
public record PackageOptionsResponse(
        UUID packageId,
        String packageName,
        List<PackageComponentOptions> components
) {
    public record PackageComponentOptions(
            UUID componentId,
            String slotName,
            boolean required,
            UUID defaultOptionId,
            String defaultLabel,
            List<PackageAlternative> alternatives
    ) {
    }

    /** priceDelta is relative to the component's default (never an absolute price) — pass it straight through, never re-derive it. */
    public record PackageAlternative(
            UUID optionId,
            String label,
            BigDecimal priceDelta
    ) {
    }
}
