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

/**
 * Dedicated AI tool-execution audit trail — deliberately separate from
 * ActivityLog (see Phase 0 reconnaissance §7): ActivityLog still gets a
 * human-readable line for every AI mutation via the existing
 * ActivityLogService, but its schema (a string + one entity pointer) can't
 * hold what this table needs to (raw tool arguments/result, approval state,
 * per-call status) without stuffing everything into free text. This table
 * is for AI observability/debugging, not the Owner-facing activity feed.
 *
 * Status: STARTED, SUCCEEDED, FAILED, BLOCKED (BLOCKED = the model asked
 * for a tool that isn't in the registered allow-list — never executed).
 * arguments_json/result_json are plain text columns holding serialized
 * JSON (same "plain text, not a mapped JSON type" posture as
 * EcommerceOrder.rawPayload — nothing queries inside them via SQL).
 * NEVER put an API key/secret in either column.
 */
@Entity
@Table(name = "ai_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "tool_name", nullable = false, length = 60)
    private String toolName;

    @Column(name = "arguments_json", columnDefinition = "text")
    private String argumentsJson;

    @Column(name = "result_json", columnDefinition = "text")
    private String resultJson;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "STARTED";

    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private boolean requiresApproval = false;

    // Nullable on purpose — most tool calls never go through an approval
    // step at all (unlike PendingApproval's own required boolean), so null
    // means "not applicable," not "pending."
    @Column
    private Boolean approved;

    @Column(name = "resulting_entity_type", length = 50)
    private String resultingEntityType;

    @Column(name = "resulting_entity_id")
    private UUID resultingEntityId;

    // ---- Phase 3A: channel audit context — all three nullable/additive,
    // unset for every WEB_DEMO action (unchanged from Phase 1/2). Lets a
    // mutation be traced end-to-end: external message -> AI response ->
    // this tool call -> resulting booking, on whichever channel it came in
    // on (§18 of the spec). channel is a plain varchar copy of the
    // conversation's AiChannel name, same "String, not a foreign enum"
    // posture as every other status/category column in this schema.
    @Column(length = 20)
    private String channel;

    @Column(name = "channel_binding_id")
    private UUID channelBindingId;

    @Column(name = "external_message_id", length = 200)
    private String externalMessageId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
