package com.ratel.rbms.dto;

import com.ratel.rbms.entity.Supplier;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String notes,
        Instant createdAt
) {
    public static SupplierResponse from(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getNotes(), s.getCreatedAt());
    }
}
