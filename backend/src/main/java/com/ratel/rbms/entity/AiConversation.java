package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One customer conversation thread. Phase 1 only ever creates channel
 * WEB_DEMO (the dashboard's internal Test AI panel) — WHATSAPP/INSTAGRAM/
 * FACEBOOK/PHONE/SMS/EMAIL are reserved values for later phases, not wired
 * to anything yet. Status: ACTIVE, ESCALATED, CLOSED.
 */
@Entity
@Table(name = "ai_conversations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    // Nullable — a conversation can exist before the customer is identified.
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String channel = "WEB_DEMO";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    // Tracking-only, no FK — mirrors ServiceOrder.assignedStaffId's own
    // "not a referential constraint" posture.
    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "last_message_at", nullable = false)
    @Builder.Default
    private Instant lastMessageAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
