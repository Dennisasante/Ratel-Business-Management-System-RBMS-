package com.ratel.rbms.dto;

import com.ratel.rbms.entity.StaffMember;

import java.time.Instant;
import java.util.UUID;

public record StaffMemberResponse(
        UUID id,
        String fullName,
        String phone,
        String notes,
        boolean active,
        Instant createdAt
) {
    public static StaffMemberResponse from(StaffMember s) {
        return new StaffMemberResponse(
                s.getId(),
                s.getFullName(),
                s.getPhone(),
                s.getNotes(),
                s.isActive(),
                s.getCreatedAt()
        );
    }
}
