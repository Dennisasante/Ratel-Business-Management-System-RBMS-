package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Super Admin only (spec §6/§29) — business_id itself comes from the URL
 * path, never from this body, matching every other Platform mutation in
 * this codebase. accessToken is write-only: it's encrypted on save and
 * never echoed back by anything that reads this binding afterward.
 */
public record WhatsAppBindingCreateRequest(
        String whatsappBusinessAccountId,

        @NotBlank(message = "Phone Number ID is required")
        String phoneNumberId,

        String displayName,

        @NotBlank(message = "Access token is required")
        String accessToken,

        boolean active
) {
}
