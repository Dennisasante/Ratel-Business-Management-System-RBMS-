package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AdminResetPasswordResponse;
import com.ratel.rbms.dto.CustomWigRequestDetailResponse;
import com.ratel.rbms.dto.CustomWigRequestResponse;
import com.ratel.rbms.dto.ExpenseResponse;
import com.ratel.rbms.dto.PaymentTransactionResponse;
import com.ratel.rbms.dto.PlatformBusinessBillingUpdateRequest;
import com.ratel.rbms.dto.PlatformBusinessDetailResponse;
import com.ratel.rbms.dto.PlatformBusinessSummaryResponse;
import com.ratel.rbms.dto.PlatformCustomerSummaryResponse;
import com.ratel.rbms.dto.PlatformSaleSummaryResponse;
import com.ratel.rbms.dto.PlatformServiceOrderSummaryResponse;
import com.ratel.rbms.dto.SaleResponse;
import com.ratel.rbms.dto.ServiceOrderResponse;
import com.ratel.rbms.dto.SubscriptionPaymentResponse;
import com.ratel.rbms.dto.UpdateUserStatusRequest;
import com.ratel.rbms.service.PlatformBusinessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    public List<PaymentTransactionResponse> paymentTransactions(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        return platformBusinessService.getPaymentTransactions(id, fromInstant, toInstant);
    }

    @GetMapping("/{id}/subscription-payments")
    public List<SubscriptionPaymentResponse> subscriptionPayments(@PathVariable UUID id) {
        return platformBusinessService.getSubscriptionPayments(id);
    }

    @PostMapping("/{businessId}/payment-transactions/{transactionId}/verify")
    public ResponseEntity<Void> verifyPaymentTransaction(@PathVariable UUID businessId, @PathVariable UUID transactionId) {
        platformBusinessService.verifyPaymentTransaction(currentAdminId(), businessId, transactionId);
        return ResponseEntity.noContent().build();
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

    // Data-cleanup panel — list + delete individual test records a staff
    // member created on a real business's account.
    @GetMapping("/{id}/service-orders")
    public List<PlatformServiceOrderSummaryResponse> serviceOrders(@PathVariable UUID id) {
        return platformBusinessService.listServiceOrders(id);
    }

    @DeleteMapping("/{businessId}/service-orders/{orderId}")
    public ResponseEntity<Void> deleteServiceOrder(@PathVariable UUID businessId, @PathVariable UUID orderId) {
        platformBusinessService.deleteServiceOrder(currentAdminId(), businessId, orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/sales")
    public List<PlatformSaleSummaryResponse> sales(@PathVariable UUID id) {
        return platformBusinessService.listSales(id);
    }

    @DeleteMapping("/{businessId}/sales/{saleId}")
    public ResponseEntity<Void> deleteSale(@PathVariable UUID businessId, @PathVariable UUID saleId) {
        platformBusinessService.deleteSale(currentAdminId(), businessId, saleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/customers")
    public List<PlatformCustomerSummaryResponse> customers(@PathVariable UUID id) {
        return platformBusinessService.listCustomers(id);
    }

    @DeleteMapping("/{businessId}/customers/{customerId}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID businessId, @PathVariable UUID customerId) {
        platformBusinessService.deleteCustomer(currentAdminId(), businessId, customerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/expenses")
    public List<ExpenseResponse> expenses(@PathVariable UUID id) {
        return platformBusinessService.listExpenses(id);
    }

    @GetMapping("/{id}/custom-wig-requests")
    public List<CustomWigRequestResponse> customWigRequests(@PathVariable UUID id) {
        return platformBusinessService.listCustomWigRequests(id);
    }

    // Full-record detail views — "for support sake": tapping a row shows
    // everything the business's own Owner would see, not just the
    // summary/delete row above.
    @GetMapping("/{businessId}/sales/{saleId}")
    public SaleResponse saleDetail(@PathVariable UUID businessId, @PathVariable UUID saleId) {
        return platformBusinessService.getSaleDetail(businessId, saleId);
    }

    @GetMapping("/{businessId}/service-orders/{orderId}")
    public ServiceOrderResponse serviceOrderDetail(@PathVariable UUID businessId, @PathVariable UUID orderId) {
        return platformBusinessService.getServiceOrderDetail(businessId, orderId);
    }

    @GetMapping("/{businessId}/custom-wig-requests/{requestId}")
    public CustomWigRequestDetailResponse customWigRequestDetail(@PathVariable UUID businessId, @PathVariable UUID requestId) {
        return platformBusinessService.getCustomWigRequestDetail(businessId, requestId);
    }

    private UUID currentAdminId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
