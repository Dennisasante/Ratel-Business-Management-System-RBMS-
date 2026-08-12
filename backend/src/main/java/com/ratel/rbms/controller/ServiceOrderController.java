package com.ratel.rbms.controller;

import com.ratel.rbms.dto.CheckoutResponse;
import com.ratel.rbms.dto.MobileMoneyChargeRequest;
import com.ratel.rbms.dto.MobileMoneyChargeResponse;
import com.ratel.rbms.dto.ServiceOrderPhotoResponse;
import com.ratel.rbms.dto.ServiceOrderReportResponse;
import com.ratel.rbms.dto.ServiceOrderRequest;
import com.ratel.rbms.dto.ServiceOrderResponse;
import com.ratel.rbms.dto.ServiceOrderStatusRequest;
import com.ratel.rbms.dto.ServiceOrderUpdateRequest;
import com.ratel.rbms.dto.VerifyPaymentRequest;
import com.ratel.rbms.entity.enums.ServiceOrderStatus;
import com.ratel.rbms.service.ServiceOrderPhotoService;
import com.ratel.rbms.service.ServiceOrderReportService;
import com.ratel.rbms.service.ServiceOrderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-orders")
public class ServiceOrderController {

    private final ServiceOrderService serviceOrderService;
    private final ServiceOrderReportService serviceOrderReportService;
    private final ServiceOrderPhotoService serviceOrderPhotoService;

    public ServiceOrderController(
            ServiceOrderService serviceOrderService,
            ServiceOrderReportService serviceOrderReportService,
            ServiceOrderPhotoService serviceOrderPhotoService
    ) {
        this.serviceOrderService = serviceOrderService;
        this.serviceOrderReportService = serviceOrderReportService;
        this.serviceOrderPhotoService = serviceOrderPhotoService;
    }

    @GetMapping
    public List<ServiceOrderResponse> list(
            @RequestParam(required = false) UUID serviceTypeId,
            @RequestParam(required = false) ServiceOrderStatus status,
            @RequestParam(required = false, defaultValue = "0") int page
    ) {
        return serviceOrderService.list(serviceTypeId, status, page);
    }

    // Business-wide revenue/turnaround — unlike list()/get() below, this isn't
    // (and shouldn't be) scoped to a STAFF user's own assigned orders, so it's
    // gated to the same roles as ReportController's sales-side equivalent.
    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
    public ServiceOrderReportResponse report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        // Default window: month-to-date, matching ReportController's sales report default.
        LocalDate effectiveFrom = from != null ? from : effectiveTo.with(TemporalAdjusters.firstDayOfMonth());
        return serviceOrderReportService.report(effectiveFrom, effectiveTo);
    }

    @GetMapping("/{id}")
    public ServiceOrderResponse get(@PathVariable UUID id) {
        return serviceOrderService.get(id);
    }

    @PostMapping
    public ResponseEntity<ServiceOrderResponse> create(@Valid @RequestBody ServiceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOrderService.create(request));
    }

    @PatchMapping("/{id}")
    public ServiceOrderResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceOrderUpdateRequest request) {
        return serviceOrderService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ServiceOrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody ServiceOrderStatusRequest request) {
        return serviceOrderService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/resend-ready-email")
    public ServiceOrderResponse resendReadyEmail(@PathVariable UUID id) {
        return serviceOrderService.resendReadyEmail(id);
    }

    @PostMapping("/{id}/checkout")
    public CheckoutResponse startPayment(@PathVariable UUID id) {
        return serviceOrderService.startPayment(id);
    }

    @PostMapping("/{id}/charge-mobile-money")
    public MobileMoneyChargeResponse chargeMobileMoney(@PathVariable UUID id, @Valid @RequestBody MobileMoneyChargeRequest request) {
        return serviceOrderService.chargeMobileMoney(id, request);
    }

    @PostMapping("/verify")
    public ServiceOrderResponse verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        return serviceOrderService.verifyPayment(request.reference());
    }

    @PostMapping("/{id}/mark-paid")
    public ServiceOrderResponse markPaid(@PathVariable UUID id) {
        return serviceOrderService.markPaid(id);
    }

    @GetMapping("/{id}/photos")
    public List<ServiceOrderPhotoResponse> listPhotos(@PathVariable UUID id) {
        return serviceOrderPhotoService.list(id);
    }

    @PostMapping("/{id}/photos")
    public ResponseEntity<ServiceOrderPhotoResponse> uploadPhoto(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOrderPhotoService.upload(id, file));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        serviceOrderPhotoService.delete(id, photoId);
        return ResponseEntity.noContent().build();
    }
}
