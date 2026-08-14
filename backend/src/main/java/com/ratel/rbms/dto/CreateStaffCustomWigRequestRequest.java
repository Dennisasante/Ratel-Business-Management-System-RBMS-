package com.ratel.rbms.dto;

import java.math.BigDecimal;

// Not @Valid-annotated on its controller — arrives as a JSON string inside a
// multipart request (alongside the optional inspiration photo file), same
// reasoning as SubmitCustomWigRequestRequest; CustomWigRequestService
// validates it manually.
//
// Always free text + a staff-entered price — deliberately doesn't offer the
// public widget's attribute/option picker at all. A request logged from an
// Instagram DM or a phone call is exactly what the customer said, not a
// pricing-rule lookup; making staff configure attributes first (the original
// design) just to log "24 inches HD wig, GHS 800" was the whole complaint.
public record CreateStaffCustomWigRequestRequest(
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        String source,
        String description,
        BigDecimal price,
        String notes
) {
}
