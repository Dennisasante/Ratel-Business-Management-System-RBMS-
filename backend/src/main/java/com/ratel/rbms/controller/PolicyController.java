package com.ratel.rbms.controller;

import com.ratel.rbms.dto.DecisionRequest;
import com.ratel.rbms.dto.PolicySummaryResponse;
import com.ratel.rbms.dto.RequestPolicyOverrideRequest;
import com.ratel.rbms.entity.Policy;
import com.ratel.rbms.entity.PolicyVersion;
import com.ratel.rbms.repository.PolicyVersionRepository;
import com.ratel.rbms.service.ApprovalGateService;
import com.ratel.rbms.service.PolicyEngine;
import com.ratel.rbms.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Talia Unified Platform, Phase 4 — staff-facing surface for the non-AI flow (Revision 4 §8):
 * a read-only pre-flight ({@code /applicable}) plus the staff-override request/decision
 * lifecycle. Deliberately thin — the authoritative decision is always
 * {@link PolicyEngine#evaluateGate}, called from inside each transaction-creating service, never
 * from here. This controller knows nothing about AI, bookings, sales, or service orders — it is
 * wired to {@link PolicyEngine} alone, matching Revision 4 §7's "PolicyEngine has zero AI-specific
 * parameters" boundary and its staff-facing mirror.
 */
@RestController
@RequestMapping("/api/policies")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class PolicyController {

    private final PolicyEngine policyEngine;
    private final PolicyVersionRepository policyVersionRepository;
    private final ApprovalGateService approvalGateService;

    public PolicyController(PolicyEngine policyEngine, PolicyVersionRepository policyVersionRepository,
                             ApprovalGateService approvalGateService) {
        this.policyEngine = policyEngine;
        this.policyVersionRepository = policyVersionRepository;
        this.approvalGateService = approvalGateService;
    }

    // Layer A only (Revision 4 §5) — advisory, never authoritative.
    @GetMapping("/applicable")
    public java.util.List<PolicySummaryResponse> applicable(
            @RequestParam String appliesToAction, @RequestParam(required = false) UUID offeringId) {
        UUID businessId = TenantContext.getBusinessId();
        return policyEngine.applicablePolicies(businessId, appliesToAction, offeringId).stream()
                .map(this::toSummary)
                .toList();
    }

    private PolicySummaryResponse toSummary(Policy policy) {
        PolicyVersion current = policyVersionRepository.findTopByPolicyIdOrderByVersionNumberDesc(policy.getId())
                .orElseThrow();
        return new PolicySummaryResponse(policy.getId(), policy.getPolicyKey(), policy.getAppliesToAction(),
                policy.getOfferingId(), current.getVersionNumber(), current.getTitle(), current.getContent(),
                current.isRequiresAcknowledgement(), current.isBlocksTransaction(), current.isStaffOverridable());
    }

    @PostMapping("/overrides")
    public ResponseEntity<Map<String, UUID>> requestOverride(@Valid @RequestBody RequestPolicyOverrideRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        UUID overrideId = policyEngine.requestOverride(businessId, req.commitmentReference(), req.policyId(),
                req.policyVersionId(), TenantContext.getUserId(), req.reason(), approvalGateService.isOwner());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", overrideId));
    }

    @PostMapping("/overrides/{id}/approve")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        policyEngine.approveOverride(TenantContext.getBusinessId(), id, TenantContext.getUserId(),
                req != null ? req.note() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/overrides/{id}/reject")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        policyEngine.rejectOverride(TenantContext.getBusinessId(), id, TenantContext.getUserId(),
                req != null ? req.note() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/overrides/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        policyEngine.cancelOverride(TenantContext.getBusinessId(), id);
        return ResponseEntity.noContent().build();
    }
}
