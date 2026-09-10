package com.ratel.rbms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 3 — one row per (policy, commitment attempt): the disclosure
 * event, and — later, on the same row — its acknowledgement, if any. {@code acknowledgedAt}
 * null means shown but not yet acknowledged; this is the entire acknowledgement lifecycle, no
 * separate entity. {@code actor}/{@code channel} describe who/what performed the DISCLOSURE;
 * {@code acknowledgedByType}/{@code acknowledgedById} separately describe who recorded the
 * ACKNOWLEDGEMENT — the two are different events and may have different actors (e.g. staff
 * relaying a policy by phone, the customer verbally agreeing, staff recording that agreement).
 *
 * <p>No {@code @CreationTimestamp} — {@code disclosedAt} already serves as this row's own
 * creation marker; a second, always-identical column would be redundant.
 */
@Entity
@Table(name = "policy_disclosures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyDisclosure {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "policy_version_id", nullable = false)
    private UUID policyVersionId;

    @Column(name = "commitment_reference", nullable = false)
    private UUID commitmentReference;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "transaction_type", length = 30)
    private String transactionType;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false, length = 30)
    private String actor;

    @Column(length = 20)
    private String channel;

    @Column(name = "disclosed_at", nullable = false)
    @Builder.Default
    private Instant disclosedAt = Instant.now();

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by_type", length = 20)
    private String acknowledgedByType;

    @Column(name = "acknowledged_by_id")
    private UUID acknowledgedById;
}
