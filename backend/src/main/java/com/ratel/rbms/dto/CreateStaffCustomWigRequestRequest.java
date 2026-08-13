package com.ratel.rbms.dto;

import java.util.List;

// Not @Valid-annotated on its controller — arrives as a JSON string inside a
// multipart request (alongside the optional inspiration photo file), same
// reasoning as SubmitCustomWigRequestRequest; CustomWigRequestService
// validates it manually.
public record CreateStaffCustomWigRequestRequest(
        String customerName,
        String customerEmail,
        String customerWhatsapp,
        String source,
        List<CustomWigSelectionInput> selections,
        String notes
) {
}
