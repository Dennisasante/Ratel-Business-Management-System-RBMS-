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
 * One fact/policy/FAQ entry the AI is allowed to draw on for a specific
 * business. {@code category} is plain varchar (not a native Postgres enum),
 * matching every status-like column elsewhere in this schema — new
 * categories never need a migration. Suggested values: FAQ, BUSINESS_INFO,
 * SERVICE, POLICY, RESTAURANT, HOTEL, EVENTS, BEACH, OTHER.
 */
@Entity
@Table(name = "ai_knowledge_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiKnowledgeEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String category = "OTHER";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
