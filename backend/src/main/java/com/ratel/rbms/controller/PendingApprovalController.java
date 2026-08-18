package com.ratel.rbms.controller;

import com.ratel.rbms.dto.DecisionRequest;
import com.ratel.rbms.dto.PendingApprovalResponse;
import com.ratel.rbms.service.PendingApprovalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pending-approvals")
@PreAuthorize("hasRole('OWNER')")
public class PendingApprovalController {

    private final PendingApprovalService pendingApprovalService;

    public PendingApprovalController(PendingApprovalService pendingApprovalService) {
        this.pendingApprovalService = pendingApprovalService;
    }

    @GetMapping
    public List<PendingApprovalResponse> list() {
        return pendingApprovalService.listPending();
    }

    @PostMapping("/{id}/approve")
    public PendingApprovalResponse approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest request) {
        return pendingApprovalService.approve(id, request != null ? request.note() : null);
    }

    @PostMapping("/{id}/reject")
    public PendingApprovalResponse reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest request) {
        return pendingApprovalService.reject(id, request != null ? request.note() : null);
    }
}
