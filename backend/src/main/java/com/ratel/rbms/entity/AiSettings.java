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
 * One configuration row per business — {@code business_id} is unique, so
 * there is at most one of these ever (same shape as BusinessIntegrations).
 * {@code active} is a business-level convenience flag; the authoritative
 * feature gate is still Business.enabledModules containing "AI" (see
 * ModuleAccessService) — this flag only matters once that gate has already
 * passed, letting an Owner pause the AI without a Super Admin's involvement.
 */
@Entity
@Table(name = "ai_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSettings {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false, unique = true)
    private UUID businessId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "agent_name", nullable = false, length = 80)
    @Builder.Default
    private String agentName = "Tallia";

    @Column(columnDefinition = "text")
    private String greeting;

    @Column(length = 50)
    private String tone;

    // Never sent to the customer-facing side directly — only ever folded
    // into the server-assembled system prompt. See AiChatService.
    @Column(name = "system_instructions", columnDefinition = "text")
    private String systemInstructions;

    @Column(name = "human_handoff_enabled", nullable = false)
    @Builder.Default
    private boolean humanHandoffEnabled = true;

    @Column(name = "human_handoff_message", columnDefinition = "text")
    private String humanHandoffMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
