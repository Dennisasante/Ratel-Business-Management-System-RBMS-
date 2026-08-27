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
 * One message in an AiConversation. Role: USER, ASSISTANT, SYSTEM, TOOL.
 * Deliberately minimal — no hidden chain-of-thought, no raw model
 * internals; {@code content} is exactly what a transcript viewer should be
 * allowed to show. business_id is a denormalized copy of the parent
 * conversation's own business_id (not resolved via a join) so a message row
 * can always be tenant-checked directly, matching payment_transactions'
 * own denormalization of business_id alongside its source_id pointer.
 */
@Entity
@Table(name = "ai_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMessage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    // ---- Phase 3A: channel idempotency — both nullable/additive, unset on
    // every WEB_DEMO message. channelBindingId is denormalized directly onto
    // this row (same reasoning as businessId above) so
    // findByChannelBindingIdAndExternalMessageId never needs a join through
    // ai_conversations. The pair is uniquely constrained at the DB level
    // (see V50) — this is what makes "the same external message is never
    // processed twice" a guarantee, not just application discipline.
    @Column(name = "channel_binding_id")
    private UUID channelBindingId;

    @Column(name = "external_message_id", length = 200)
    private String externalMessageId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
