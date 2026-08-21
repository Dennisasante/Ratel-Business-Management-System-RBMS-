package com.ratel.rbms.dto;

import jakarta.validation.constraints.NotBlank;

public record PushSubscribeRequest(
        @NotBlank String endpoint,
        @NotBlank String p256dh,
        @NotBlank String auth
) {
}
