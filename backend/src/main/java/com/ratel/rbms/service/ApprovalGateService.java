package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.entity.PendingApproval;
import com.ratel.rbms.entity.enums.Role;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PendingApprovalRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Shared by SaleService/ServiceOrderService/CustomWigRequestService — the one
// piece of the approval workflow every gated domain method calls into. Doesn't
// know how to apply an approved change (that stays in each domain service,
// via applyApproved*() methods called only by PendingApprovalService) — this
// class only knows how to decide "does this need approval" and, if so, log it.
@Service
public class ApprovalGateService {

    private final PendingApprovalRepository pendingApprovalRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ApprovalGateService(
            PendingApprovalRepository pendingApprovalRepository,
            NotificationService notificationService,
            ObjectMapper objectMapper
    ) {
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public boolean isOwner() {
        return Role.OWNER.name().equals(TenantContext.getRole());
    }

    // REQUIRES_NEW is essential, not optional: the caller's own @Transactional
    // method is about to unwind via ApprovalRequiredException right after this
    // returns, so this insert must commit independently *before* that unwind
    // happens — otherwise the approval request itself would roll back along
    // with the change it was meant to gate. Must be called cross-bean (never
    // self-invoked from inside this same class) or Spring's proxy won't honor
    // REQUIRES_NEW at all.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID queueForApproval(
            PendingApproval.SourceType sourceType,
            PendingApproval.ActionType actionType,
            UUID sourceId,
            Object payload,
            String summary
    ) {
        UUID businessId = TenantContext.getBusinessId();
        PendingApproval pa = PendingApproval.builder()
                .businessId(businessId)
                .sourceType(sourceType)
                .actionType(actionType)
                .sourceId(sourceId)
                .payload(writePayload(payload))
                .summary(summary)
                .status(PendingApproval.Status.PENDING)
                .requestedBy(TenantContext.getUserId())
                .build();
        pa = pendingApprovalRepository.save(pa);

        notificationService.create(businessId, "APPROVAL_REQUESTED", "Approval needed: " + summary,
                null, "PENDING_APPROVAL", pa.getId());

        return pa.getId();
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't submit this for approval. Please try again.");
        }
    }
}
