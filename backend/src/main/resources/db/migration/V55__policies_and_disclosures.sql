-- Talia Unified Platform — Phase 3: the Policy / PolicyVersion / PolicyCommitment /
-- PolicyDisclosure model (architecture proposal §G), reconciled across four review rounds
-- before implementation. Purely additive: no existing table is altered. Nothing here is read by
-- any existing controller, AiTool, or transaction flow — see the approved design for what a
-- later, separately-approved phase does with it.
--
-- Ownership model: business_id propagated onto every table, composite foreign keys throughout
-- (same technique as Phase 2, extended one column deeper in one place — see below).
--
-- Deletion/retirement: every FK in the Offering -> Policy -> PolicyVersion -> PolicyDisclosure
-- chain is ON DELETE RESTRICT, never CASCADE. This is audit/compliance evidence — a hard
-- delete anywhere in that chain must fail loudly, never silently take history with it.
-- Retirement is Policy.active = false, the same soft-delete convention already used elsewhere
-- in this codebase (Business.active, ServicePackage.active, AiKnowledgeEntry.active).
--
-- Version immutability: PolicyVersion is genuinely append-only, enforced at the database level
-- by the two triggers below (INSERT allowed, UPDATE/DELETE rejected) — not merely a service-
-- level convention, since this is historical evidence a future code path must not be able to
-- silently alter.

CREATE TABLE policies (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id        UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    policy_key         VARCHAR(100) NOT NULL,
    -- Free text, no CHECK constraint — deliberately open-ended (a new transaction/module type
    -- means a new value here, never a migration), matching AiKnowledgeEntry.category's own
    -- precedent.
    applies_to_action  VARCHAR(60) NOT NULL,
    -- NULL = applies business-wide to every offering under this action. Non-null = scoped to
    -- one specific Offering only.
    offering_id        UUID,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_by         UUID NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_policies_offering FOREIGN KEY (business_id, offering_id)
        REFERENCES offerings(business_id, id) ON DELETE RESTRICT,
    CONSTRAINT uq_policies_business_id_id UNIQUE (business_id, id),
    CONSTRAINT uq_policies_business_key UNIQUE (business_id, policy_key)
);
CREATE INDEX idx_policies_business_id ON policies(business_id);
CREATE INDEX idx_policies_offering_id ON policies(offering_id);

CREATE TABLE policy_versions (
    id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id               UUID NOT NULL,
    policy_id                 UUID NOT NULL,
    version_number            INT NOT NULL,
    title                     VARCHAR(200) NOT NULL,
    content                   TEXT NOT NULL,
    -- Enforcement semantics live HERE, not on Policy — this is what makes historical
    -- reconstruction possible: a later change to a policy's enforcement configuration must
    -- never alter what a past disclosure actually enforced. See PolicyDisclosure below, which
    -- points at a specific version, never at the mutable Policy row's current state.
    requires_acknowledgement  BOOLEAN NOT NULL DEFAULT TRUE,
    blocks_transaction        BOOLEAN NOT NULL DEFAULT TRUE,
    staff_overridable         BOOLEAN NOT NULL DEFAULT FALSE,
    created_by                UUID NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_policy_versions_policy FOREIGN KEY (business_id, policy_id)
        REFERENCES policies(business_id, id) ON DELETE RESTRICT,
    -- 3-column composite-FK target — this is what proves a PolicyDisclosure's policy_version_id
    -- genuinely belongs to its own policy_id, not merely that each independently exists
    -- somewhere (see fk_policy_disclosures_version below).
    CONSTRAINT uq_policy_versions_business_policy_id UNIQUE (business_id, policy_id, id),
    CONSTRAINT uq_policy_versions_policy_number UNIQUE (policy_id, version_number),
    CONSTRAINT chk_policy_versions_number_positive CHECK (version_number >= 1)
);
CREATE INDEX idx_policy_versions_policy_id ON policy_versions(policy_id);
CREATE INDEX idx_policy_versions_business_id ON policy_versions(business_id);

-- Append-only enforcement — narrow, scoped to exactly this one table and exactly this one
-- behavior. INSERT is untouched (no trigger on it); UPDATE/DELETE are unconditionally rejected,
-- regardless of caller (repository.save() on a mutated row, direct SQL, a future application
-- path — all hit this, not just the service layer's own discipline, which stays as the first
-- line of defense underneath it).
CREATE OR REPLACE FUNCTION reject_policy_version_mutation()
RETURNS TRIGGER AS $$
BEGIN
    -- ERRCODE 23001 (restrict_violation) — an integrity-constraint-violation-class SQLSTATE,
    -- deliberately not the plain-text default (P0001), so Hibernate/Spring's own SQLSTATE-based
    -- exception translation correctly surfaces this as a DataIntegrityViolationException to
    -- callers, the same exception family every other constraint violation in this schema
    -- produces, rather than a generic, uncategorized JPA error.
    RAISE EXCEPTION 'policy_versions rows are append-only — UPDATE/DELETE rejected (id=%)',
        COALESCE(OLD.id, NEW.id)
        USING ERRCODE = '23001';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_policy_versions_no_update
    BEFORE UPDATE ON policy_versions
    FOR EACH ROW EXECUTE FUNCTION reject_policy_version_mutation();

CREATE TRIGGER trg_policy_versions_no_delete
    BEFORE DELETE ON policy_versions
    FOR EACH ROW EXECUTE FUNCTION reject_policy_version_mutation();

-- A commitment_reference is minted and registered to exactly one business here (see
-- PolicyEngine.beginCommitment) before any PolicyDisclosure ever uses it — this is what makes
-- cross-business commitment reuse a real constraint violation (fk_policy_disclosures_commitment
-- below), not merely something the caller is trusted to get right.
CREATE TABLE policy_commitments (
    commitment_reference  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id           UUID NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_policy_commitments_business_ref UNIQUE (business_id, commitment_reference)
);

CREATE TABLE policy_disclosures (
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    business_id            UUID NOT NULL,
    policy_id              UUID NOT NULL,
    policy_version_id      UUID NOT NULL,
    commitment_reference   UUID NOT NULL,
    -- Plain tracking pointers, NO foreign key — matches this exact codebase's own established
    -- convention for optional cross-references (AiConversation.customerId/assignedUserId are
    -- both "plain UUID, no @ManyToOne... tracking pointer, not a mapped relationship").
    customer_id            UUID,
    conversation_id        UUID,
    -- Polymorphic transaction pointer, nullable-then-backfilled — same shape as
    -- AiAction.resultingEntityType/Id, same reasoning (no FK possible across multiple possible
    -- target tables; the real transaction doesn't exist yet at disclosure time).
    transaction_type       VARCHAR(30),
    transaction_id         UUID,
    -- Who/what performed the DISCLOSURE (AI / STAFF / CUSTOMER_SELF_SERVICE) — deliberately
    -- separate from who performed the ACKNOWLEDGEMENT below; the two are different events.
    actor                  VARCHAR(30) NOT NULL,
    channel                VARCHAR(20),
    disclosed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- NULL = shown, not yet acknowledged. Acknowledgement is a later, separate event on the
    -- same row (its only mutation, ever — everything else here is set once at creation).
    acknowledged_at        TIMESTAMPTZ,
    -- Who recorded the acknowledgement: 'CUSTOMER' (self-service) or 'STAFF' (staff-mediated,
    -- e.g. a phone booking). No FK (polymorphic across customers/users), same convention as
    -- customer_id/conversation_id above.
    acknowledged_by_type   VARCHAR(20),
    acknowledged_by_id     UUID,
    CONSTRAINT fk_policy_disclosures_policy FOREIGN KEY (business_id, policy_id)
        REFERENCES policies(business_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_policy_disclosures_version FOREIGN KEY (business_id, policy_id, policy_version_id)
        REFERENCES policy_versions(business_id, policy_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_policy_disclosures_commitment FOREIGN KEY (business_id, commitment_reference)
        REFERENCES policy_commitments(business_id, commitment_reference) ON DELETE RESTRICT,
    CONSTRAINT chk_policy_disclosures_ack_consistency
        CHECK ((acknowledged_at IS NULL) = (acknowledged_by_type IS NULL)),
    CONSTRAINT chk_policy_disclosures_ack_staff_identified
        CHECK (acknowledged_by_type IS DISTINCT FROM 'STAFF' OR acknowledged_by_id IS NOT NULL)
);
CREATE INDEX idx_policy_disclosures_commitment_reference ON policy_disclosures(commitment_reference);
CREATE INDEX idx_policy_disclosures_policy_version_id ON policy_disclosures(policy_version_id);
CREATE INDEX idx_policy_disclosures_business_id ON policy_disclosures(business_id);
CREATE INDEX idx_policy_disclosures_transaction ON policy_disclosures(transaction_type, transaction_id);
