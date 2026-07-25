package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Missing Google credential")
        String idToken
) {
}
