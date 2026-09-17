package com.ratel.rbms.dto;

import java.util.List;

// Not @Valid-annotated on its controller — arrives as a JSON string inside a
// multipart request (alongside the optional inspiration photo file), same
// reasoning as SubmitCustomWigRequestRequest; CustomWigRequestService
// validates it manually.
//
// Always free text + a staff-entered price per wig — deliberately doesn't
// offer the public widget's attribute/option picker at all. A request logged
// from an Instagram DM or a phone call is exactly what the customer said,
// not a pricing-rule lookup; making staff configure attributes first (the
// original design) just to log "24 inches HD wig, GHS 800" was the whole
// complaint. `items` is a real order — one client can want more than one wig
// (real gap found live: there was no way to log that as a single order) —
// so it's always at least one item, never a single flat description/price.
public record CreateStaffCustomWigRequestRequest(
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        String source,
        List<StaffWigItemInput> items,
        String notes
) {
}
