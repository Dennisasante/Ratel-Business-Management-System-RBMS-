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

// A non-Owner's price edit or refund on a Sale/ServiceOrder/CustomWigRequest,
// queued instead of applied — see ApprovalGateService. `payload` is a JSON
// snapshot of the original request DTO (same "JSON in a TEXT column"
// convention as CustomWigRequest.selections), replayed verbatim by
// PendingApprovalService.approve() so the applied change is byte-for-byte
// what was originally requested, not re-derived from the summary text.
@Entity
@Table(name = "pending_approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingApproval {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 300)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    public enum SourceType { SALE, SERVICE_ORDER, CUSTOM_WIG_REQUEST, EXPENSE }

    public enum ActionType { EDIT_PRICE, REFUND, EDIT }

    public enum Status { PENDING, APPROVED, REJECTED }
}
