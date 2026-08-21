package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.dto.ExpenseEditRequest;
import com.ratel.rbms.dto.PendingApprovalResponse;
import com.ratel.rbms.dto.RefundRequest;
import com.ratel.rbms.dto.SaleItemPricePayload;
import com.ratel.rbms.dto.ServiceOrderUpdateRequest;
import com.ratel.rbms.dto.UpdateFinalPriceRequest;
import com.ratel.rbms.entity.PendingApproval;
import com.ratel.rbms.entity.User;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.PendingApprovalRepository;
import com.ratel.rbms.repository.UserRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// The one seam that knows the (sourceType, actionType) -> domain-method
// mapping — approve() replays the original request verbatim against the
// matching applyApproved*() method, which bypasses each domain service's
// own approval gate (the Owner clicking Approve IS the authorization).
@Service
public class PendingApprovalService {

    private final PendingApprovalRepository pendingApprovalRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ActivityLogService activityLogService;
    private final SaleService saleService;
    private final ServiceOrderService serviceOrderService;
    private final CustomWigRequestService customWigRequestService;
    private final ExpenseService expenseService;

    public PendingApprovalService(
            PendingApprovalRepository pendingApprovalRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            ActivityLogService activityLogService,
            SaleService saleService,
            ServiceOrderService serviceOrderService,
            CustomWigRequestService customWigRequestService,
            ExpenseService expenseService
    ) {
        this.pendingApprovalRepository = pendingApprovalRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.activityLogService = activityLogService;
        this.saleService = saleService;
        this.serviceOrderService = serviceOrderService;
        this.customWigRequestService = customWigRequestService;
        this.expenseService = expenseService;
    }

    public List<PendingApprovalResponse> listPending() {
        UUID businessId = TenantContext.getBusinessId();
        return pendingApprovalRepository.findAllByBusinessIdAndStatusOrderByRequestedAtDesc(businessId, PendingApproval.Status.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PendingApprovalResponse approve(UUID id, String note) {
        PendingApproval pa = getOwned(id);
        requirePending(pa);

        applyChange(pa);

        pa.setStatus(PendingApproval.Status.APPROVED);
        pa.setDecidedBy(TenantContext.getUserId());
        pa.setDecidedAt(Instant.now());
        pa.setDecisionNote(note);
        pa = pendingApprovalRepository.save(pa);

        activityLogService.log("Approved: " + pa.getSummary(), pa.getSourceType().name(), pa.getSourceId());

        return toResponse(pa);
    }

    // Never touches the underlying Sale/ServiceOrder/CustomWigRequest —
    // nothing was ever applied to it in the first place.
    @Transactional
    public PendingApprovalResponse reject(UUID id, String note) {
        PendingApproval pa = getOwned(id);
        requirePending(pa);

        pa.setStatus(PendingApproval.Status.REJECTED);
        pa.setDecidedBy(TenantContext.getUserId());
        pa.setDecidedAt(Instant.now());
        pa.setDecisionNote(note);
        pa = pendingApprovalRepository.save(pa);

        activityLogService.log("Rejected: " + pa.getSummary(), pa.getSourceType().name(), pa.getSourceId());

        return toResponse(pa);
    }

    private void requirePending(PendingApproval pa) {
        if (pa.getStatus() != PendingApproval.Status.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This request has already been decided.");
        }
    }

    private void applyChange(PendingApproval pa) {
        switch (pa.getSourceType()) {
            case SALE -> {
                if (pa.getActionType() == PendingApproval.ActionType.REFUND) {
                    saleService.applyApprovedRefund(pa.getSourceId(), readPayload(pa, RefundRequest.class));
                } else {
                    SaleItemPricePayload payload = readPayload(pa, SaleItemPricePayload.class);
                    saleService.applyApprovedItemPriceEdit(pa.getSourceId(), payload.itemId(), payload.request());
                }
            }
            case SERVICE_ORDER -> {
                if (pa.getActionType() == PendingApproval.ActionType.REFUND) {
                    serviceOrderService.applyApprovedRefund(pa.getSourceId(), readPayload(pa, RefundRequest.class));
                } else {
                    serviceOrderService.applyApprovedUpdate(pa.getSourceId(), readPayload(pa, ServiceOrderUpdateRequest.class));
                }
            }
            case CUSTOM_WIG_REQUEST -> {
                if (pa.getActionType() == PendingApproval.ActionType.REFUND) {
                    customWigRequestService.applyApprovedRefund(pa.getSourceId(), readPayload(pa, RefundRequest.class));
                } else {
                    customWigRequestService.applyApprovedPriceUpdate(pa.getSourceId(), readPayload(pa, UpdateFinalPriceRequest.class));
                }
            }
            case EXPENSE -> expenseService.applyApprovedUpdate(pa.getSourceId(), readPayload(pa, ExpenseEditRequest.class));
        }
    }

    private <T> T readPayload(PendingApproval pa, Class<T> type) {
        try {
            return objectMapper.readValue(pa.getPayload(), type);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Couldn't apply this approval. Please try again.");
        }
    }

    private PendingApproval getOwned(UUID id) {
        return pendingApprovalRepository.findByIdAndBusinessId(id, TenantContext.getBusinessId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Approval request not found."));
    }

    private PendingApprovalResponse toResponse(PendingApproval pa) {
        String requestedByName = userRepository.findById(pa.getRequestedBy()).map(User::getFullName).orElse("Unknown");
        return PendingApprovalResponse.from(pa, requestedByName);
    }
}
