package com.ratel.rbms.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        UUID businessId,
        String businessName,
        String fullName,
        String email,
        String role,
        boolean mustChangePassword
) {
}
