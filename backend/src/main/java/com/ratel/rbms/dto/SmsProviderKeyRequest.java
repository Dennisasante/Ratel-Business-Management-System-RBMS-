package com.ratel.rbms.dto;

import jakarta.validation.constraints.Size;

public record SmsProviderKeyRequest(
        // Blank removes the saved key (the server's own key, if any, is used again).
        @Size(max = 200, message = "That key is too long")
        String apiKey
) {
}
