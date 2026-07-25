package com.ratel.rbms.dto;

import com.ratel.rbms.entity.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        boolean active,
        BigDecimal commissionRate,
        Instant createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole().name(),
                u.isActive(),
                u.getCommissionRate(),
                u.getCreatedAt()
        );
    }
}
