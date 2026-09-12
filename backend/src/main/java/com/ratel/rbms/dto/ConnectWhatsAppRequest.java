package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — the business-facing (OWNER/MANAGER) counterpart to
 * the existing Super-Admin-only {@code WhatsAppBindingCreateRequest}, same field shape and same
 * write-only-token semantics. {@code accessToken} is required to CONNECT (no prior binding exists)
 * but optional to RECONNECT/update (a blank value leaves the currently-stored token untouched) —
 * enforced in {@code WhatsAppManualConnectionInitiator}, not by bean validation here, since
 * whether it's required depends on whether a binding already exists.
 */
public record ConnectWhatsAppRequest(
        String whatsappBusinessAccountId,

        @NotBlank(message = "Phone Number ID is required")
        String phoneNumberId,

        String displayName,

        String accessToken
) {
}
