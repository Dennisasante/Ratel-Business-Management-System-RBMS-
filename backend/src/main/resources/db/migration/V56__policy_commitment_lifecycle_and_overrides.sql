-- Talia Unified Platform — Phase 4: commitment lifecycle + staff override evidence, frozen after
-- four design-review rounds (Revision 1-4 + Final Corrections) before implementation. Purely
-- additive: extends policy_commitments (Phase 3) with the fields needed to represent a single
-- transaction-attempt lifecycle and adds one new table, policy_overrides. No existing Phase 1/2/3
-- table is altered beyond this one ALTER TABLE on policy_commitments; nothing here is read by any
-- existing controller/AiTool until the corresponding service wiring (this same phase) calls it.
--
-- Semantic model (Revision 4 §1, restated): PolicyCommitment does not attempt to snapshot the
-- transaction. Its purpose is to bind one policy-evidence lifecycle to one single-use
-- transaction attempt. It carries only business_id + transaction_type (what kind of attempt)
-- + status/timestamps (the attempt's own lifecycle) — never customer/offering/quantity, which
-- already have one authoritative owner elsewhere (PolicyDisclosure, the real transaction row).

ALTER TABLE policy_commitments
    ADD COLUMN transaction_type VARCHAR(30),
    ADD COLUMN status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN completed_at     TIMESTAMPTZ;

ALTER TABLE policy_commitments
    ADD CONSTRAINT chk_policy_commitments_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED', 'INVALIDATED')),
    ADD CONSTRAINT chk_policy_commitments_completed_consistency
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL));

CREATE INDEX idx_policy_commitments_status ON policy_commitments(status);

-- Single-use, scoped staff-override evidence (Revision 4 §9 / Final Corrections §2). Never a
-- generic "manager said okay" bypass: bound to the exact (commitment_reference, policy_id,
-- policy_version_id) tuple it authorizes, single-use (consumed atomically by the gate that uses
-- it), and OWNER-approved only (Role.MANAGER has no verified approval authority anywhere in this
-- codebase today — see PendingApprovalController's own @PreAuthorize("hasRole('OWNER')") — so
-- this table does not invent one).
CREATE TABLE policy_overrides (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id            UUID NOT NULL,
    commitment_reference   UUID NOT NULL,
    policy_id              UUID NOT NULL,
    policy_version_id      UUID NOT NULL,
    requested_by           UUID NOT NULL,
    reason                 VARCHAR(500) NOT NULL,
    requested_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- PENDING -> APPROVED -> CONSUMED (terminal)
    -- PENDING -> REJECTED (terminal)
    -- PENDING|APPROVED -> CANCELLED (terminal)
    -- PENDING|APPROVED -> EXPIRED (terminal) — bulk-set by PolicyEngine.invalidateCommitment()
    -- when the owning commitment is INVALIDATED; see PolicyEngine for the exact statement.
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by             UUID,
    decided_at             TIMESTAMPTZ,
    decision_note          VARCHAR(500),
    consumed_at            TIMESTAMPTZ,
    CONSTRAINT fk_policy_overrides_commitment FOREIGN KEY (business_id, commitment_reference)
        REFERENCES policy_commitments(business_id, commitment_reference) ON DELETE RESTRICT,
    CONSTRAINT fk_policy_overrides_policy FOREIGN KEY (business_id, policy_id)
        REFERENCES policies(business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_policy_overrides_version FOREIGN KEY (business_id, policy_id, policy_version_id)
        REFERENCES policy_versions(business_id, policy_id, id) ON DELETE RESTRICT,
    CONSTRAINT chk_policy_overrides_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CONSUMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_policy_overrides_rejected_decided
        CHECK (status != 'REJECTED' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT chk_policy_overrides_consumed_decided
        CHECK (status != 'CONSUMED' OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT chk_policy_overrides_consumed_at_consistency
        CHECK ((status = 'CONSUMED') = (consumed_at IS NOT NULL))
);

-- At most one active (PENDING/APPROVED) override per exact blocking requirement — a caller must
-- let the current one resolve (approve/reject/cancel/expire) before requesting another, rather
-- than accumulating duplicate concurrent requests for the same tuple.
CREATE UNIQUE INDEX uq_policy_overrides_active_tuple
    ON policy_overrides(business_id, commitment_reference, policy_id, policy_version_id)
    WHERE status IN ('PENDING', 'APPROVED');

CREATE INDEX idx_policy_overrides_business_id ON policy_overrides(business_id);
CREATE INDEX idx_policy_overrides_commitment_reference ON policy_overrides(commitment_reference);
CREATE INDEX idx_policy_overrides_status ON policy_overrides(status);
