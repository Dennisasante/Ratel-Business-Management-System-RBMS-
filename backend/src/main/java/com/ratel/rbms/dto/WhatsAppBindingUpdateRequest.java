package com.ratel.rbms.dto;

/**
 * Every field optional/nullable — null means "leave unchanged." accessToken
 * is write-only and rotates the stored credential only when a non-blank
 * value is supplied; the current token is never returned for comparison, so
 * there is no "edit in place" for it, only "replace."
 */
public record WhatsAppBindingUpdateRequest(
        String whatsappBusinessAccountId,
        String phoneNumberId,
        String displayName,
        String accessToken,
        Boolean active
) {
}
