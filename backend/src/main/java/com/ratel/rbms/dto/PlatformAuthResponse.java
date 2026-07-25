package com.ratel.rbms.dto;

import java.util.UUID;

public record PlatformAuthResponse(
        String token,
        UUID adminId,
        String fullName,
        String email,
        boolean mustChangePassword
) {
}
