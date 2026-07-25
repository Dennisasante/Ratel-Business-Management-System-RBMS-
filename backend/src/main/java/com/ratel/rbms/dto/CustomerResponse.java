package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        String notes,
        BigDecimal totalSpent,
        int purchaseCount,
        Instant createdAt
) {
    public static CustomerResponse from(Customer c, BigDecimal totalSpent, int purchaseCount) {
        return new CustomerResponse(
                c.getId(),
                c.getFullName(),
                c.getPhone(),
                c.getEmail(),
                c.getNotes(),
                totalSpent,
                purchaseCount,
                c.getCreatedAt()
        );
    }
}
