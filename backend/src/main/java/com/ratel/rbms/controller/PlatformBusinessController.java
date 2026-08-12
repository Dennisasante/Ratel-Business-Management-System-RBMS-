package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AdminResetPasswordResponse;
import com.ratel.rbms.dto.PaymentTransactionResponse;
import com.ratel.rbms.dto.PlatformBusinessBillingUpdateRequest;
import com.ratel.rbms.dto.PlatformBusinessDetailResponse;
import com.ratel.rbms.dto.PlatformBusinessSummaryResponse;
import com.ratel.rbms.dto.UpdateUserStatusRequest;
import com.ratel.rbms.service.PlatformBusinessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform/businesses")
public class PlatformBusinessController {

    private final PlatformBusinessService platformBusinessService;

    public PlatformBusinessController(PlatformBusinessService platformBusinessService) {
        this.platformBusinessService = platformBusinessService;
    }

    @GetMapping
    public List<PlatformBusinessSummaryResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active
    ) {
        return platformBusinessService.search(query, active);
    }

    @GetMapping("/{id}")
    public PlatformBusinessDetailResponse detail(@PathVariable UUID id) {
        return platformBusinessService.getDetail(id);
    }

    @GetMapping("/{id}/payment-transactions")
    public List<PaymentTransactionResponse> paymentTransactions(@PathVariable UUID id) {
        return platformBusinessService.getPaymentTransactions(id);
    }

    @PatchMapping("/{id}/status")
    public PlatformBusinessSummaryResponse setStatus(@PathVariable UUID id, @RequestBody UpdateUserStatusRequest request) {
        return platformBusinessService.setActive(currentAdminId(), id, request.active());
    }

    @PatchMapping("/{id}/billing")
    public PlatformBusinessDetailResponse updateBilling(@PathVariable UUID id, @RequestBody PlatformBusinessBillingUpdateRequest request) {
        return platformBusinessService.updateBilling(currentAdminId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        platformBusinessService.deleteBusiness(currentAdminId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{businessId}/users/{userId}/reset-password")
    public AdminResetPasswordResponse resetUserPassword(@PathVariable UUID businessId, @PathVariable UUID userId) {
        return platformBusinessService.resetUserPassword(currentAdminId(), businessId, userId);
    }

    private UUID currentAdminId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
