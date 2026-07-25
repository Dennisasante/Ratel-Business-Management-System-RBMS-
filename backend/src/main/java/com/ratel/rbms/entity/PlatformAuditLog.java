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

@Entity
@Table(name = "platform_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(nullable = false, length = 500)
    private String action;

    // Deliberately not a live FK — this row needs to outlive the business it
    // describes (e.g. "Deleted business X"), so business_id is just a plain
    // reference plus a name snapshot rather than something that cascades away.
    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "business_name_snapshot")
    private String businessNameSnapshot;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
