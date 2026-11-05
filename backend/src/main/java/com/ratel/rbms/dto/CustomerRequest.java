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
        String source,
        // Null leaves it as it is. True = does not want promotional texts.
        Boolean smsOptOut
) {
    // Keeps the five-field form working for every existing caller.
    public CustomerRequest(String fullName, String phone, String email, String notes, String source) {
        this(fullName, phone, email, notes, source, null);
    }
}
