package com.ratel.rbms.dto;

/** Result of validating a binding's phone number/token against the real Graph API — never sends a customer-facing message. */
public record WhatsAppConnectionTestResponse(
        boolean success,
        String displayPhoneNumber,
        String verifiedName,
        String errorMessage
) {
}
