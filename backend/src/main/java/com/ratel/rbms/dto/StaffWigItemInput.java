package com.ratel.rbms.dto;

import java.math.BigDecimal;

// One wig within a staff-logged CreateStaffCustomWigRequestRequest — see
// that record's own comment. Not @Valid-annotated for the same reason as
// its parent (arrives inside a multipart JSON string, validated manually
// in CustomWigRequestService).
public record StaffWigItemInput(
        String description,
        BigDecimal price
) {
}
