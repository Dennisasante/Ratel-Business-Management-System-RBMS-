package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record PlatformLoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
