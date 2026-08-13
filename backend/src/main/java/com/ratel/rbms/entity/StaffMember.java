package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

// A name to assign work to — no login, no password, no role. Existing
// STAFF-role User accounts were converted into these (same id, see
// V34__staff_members.sql) and had their login disabled; this is now the only
// way to add staff going forward. See ServiceOrder.assignedStaffId.
@Entity
@Table(name = "staff_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMember {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
