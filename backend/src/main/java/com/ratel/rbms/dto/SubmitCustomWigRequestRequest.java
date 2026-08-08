package com.ratel.rbms.dto;

import java.util.List;

// Not @Valid-annotated on its controller — this arrives as a JSON string
// inside a multipart request (alongside the optional inspiration photo file),
// not a typed @RequestBody, so CustomWigRequestService validates it manually.
public record SubmitCustomWigRequestRequest(
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        List<CustomWigSelectionInput> selections,
        String notes
) {
}
