package com.ratel.rbms.dto;

import java.math.BigDecimal;

// Snapshotted at submission time — an attribute/option renamed or repriced
// later must never rewrite a past request's history. Also doubles as the
// exact JSON shape persisted in custom_wig_requests.selections.
public record CustomWigSelectionResponse(
        String attributeName,
        String optionLabel,
        BigDecimal priceModifier
) {
}
