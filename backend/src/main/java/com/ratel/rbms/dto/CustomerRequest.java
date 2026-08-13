package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank(message = "Customer name is required")
        String fullName,

        String phone,

        String email,

        String notes,

        // Walk-in/Instagram/WhatsApp/Facebook/Referral/Website/Other — free
        // text, optional.
        String source
) {
}
