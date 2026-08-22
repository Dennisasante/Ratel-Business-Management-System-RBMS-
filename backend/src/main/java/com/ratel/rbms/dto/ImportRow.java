package com.ratel.rbms.dto;

import java.math.BigDecimal;
import java.util.List;

// One parsed row from an inventory import file. Carries both the parsed
// data AND its own validation result — returned from preview() as-is, then
// sent back verbatim by the frontend on confirm() (the file itself is never
// re-uploaded; the reviewed rows are the source of truth for what to save).
public record ImportRow(
        int rowNumber,
        String name,
        String category,
        String sku,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        Integer quantity,
        Integer lowStockThreshold,
        String supplierName,
        boolean valid,
        List<String> errors
) {
}
