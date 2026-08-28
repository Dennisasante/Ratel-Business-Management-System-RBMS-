package com.ratel.rbms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratel.rbms.dto.CreateStaffCustomWigRequestRequest;
import com.ratel.rbms.dto.CustomWigRequestDetailResponse;
import com.ratel.rbms.dto.CustomWigRequestResponse;
import com.ratel.rbms.dto.CustomWigStatusUpdateRequest;
import com.ratel.rbms.dto.DeclineCustomWigRequestRequest;
import com.ratel.rbms.dto.MobileMoneyChargeRequest;
import com.ratel.rbms.dto.MobileMoneyChargeResponse;
import com.ratel.rbms.dto.QuoteCustomWigRequestRequest;
import com.ratel.rbms.dto.RecordPaymentRequest;
import com.ratel.rbms.dto.RefundRequest;
import com.ratel.rbms.dto.SubmitOtpRequest;
import com.ratel.rbms.dto.UpdateFinalPriceRequest;
import com.ratel.rbms.dto.VerifyPaymentRequest;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.service.CustomWigRequestService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/custom-wig-requests")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class CustomWigRequestController {

    private final CustomWigRequestService customWigRequestService;
    private final ObjectMapper objectMapper;

    public CustomWigRequestController(CustomWigRequestService customWigRequestService, ObjectMapper objectMapper) {
        this.customWigRequestService = customWigRequestService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<CustomWigRequestResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        if (from == null && to == null) {
            return customWigRequestService.list();
        }
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        return customWigRequestService.list(fromInstant, toInstant);
    }

    // Staff logging a request that arrived through an informal channel
    // (Instagram DM, WhatsApp, a phone call) — same multipart shape as the
    // public submission (JSON "payload" part plus an optional "photo" file
    // part), see PublicCustomWigController.submit for why it's not @RequestBody.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CustomWigRequestResponse> create(
            @RequestParam("payload") String payloadJson,
            @RequestParam(value = "photo", required = false) MultipartFile photo
    ) {
        CreateStaffCustomWigRequestRequest payload;
        try {
            payload = objectMapper.readValue(payloadJson, CreateStaffCustomWigRequestRequest.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Malformed request.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(customWigRequestService.createByStaff(payload, photo));
    }

    @GetMapping("/{id}")
    public CustomWigRequestDetailResponse get(@PathVariable UUID id) {
        return customWigRequestService.get(id);
    }

    @PatchMapping("/{id}/quote")
    public CustomWigRequestResponse quote(@PathVariable UUID id, @Valid @RequestBody QuoteCustomWigRequestRequest request) {
        return customWigRequestService.quote(id, request.finalPrice(), request.message());
    }

    @PatchMapping("/{id}/price")
    public CustomWigRequestResponse updatePrice(@PathVariable UUID id, @Valid @RequestBody UpdateFinalPriceRequest request) {
        return customWigRequestService.updatePrice(id, request);
    }

    @PatchMapping("/{id}/decline")
    public CustomWigRequestResponse decline(@PathVariable UUID id, @RequestBody DeclineCustomWigRequestRequest request) {
        return customWigRequestService.decline(id, request.message());
    }

    @PatchMapping("/{id}/accept")
    public CustomWigRequestResponse accept(@PathVariable UUID id) {
        return customWigRequestService.accept(id);
    }

    @PatchMapping("/{id}/status")
    public CustomWigRequestResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody CustomWigStatusUpdateRequest request) {
        return customWigRequestService.moveToStage(id, request.status());
    }

    @PostMapping("/{id}/charge-mobile-money")
    public MobileMoneyChargeResponse chargeMobileMoney(@PathVariable UUID id, @Valid @RequestBody MobileMoneyChargeRequest request) {
        return customWigRequestService.chargeMobileMoney(id, request);
    }

    @PostMapping("/submit-mobile-money-otp")
    public CustomWigRequestResponse submitMobileMoneyOtp(@Valid @RequestBody SubmitOtpRequest request) {
        return customWigRequestService.submitMobileMoneyOtp(request.reference(), request.otp());
    }

    @PostMapping("/verify")
    public CustomWigRequestResponse verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        return customWigRequestService.verifyPayment(request.reference());
    }

    @PostMapping("/{id}/mark-paid")
    public CustomWigRequestResponse markPaid(@PathVariable UUID id) {
        return customWigRequestService.markPaid(id);
    }

    @PostMapping("/{id}/record-payment")
    public CustomWigRequestResponse recordPayment(@PathVariable UUID id, @Valid @RequestBody RecordPaymentRequest request) {
        return customWigRequestService.recordPayment(id, request);
    }

    @PostMapping("/{id}/refund")
    public CustomWigRequestResponse refund(@PathVariable UUID id, @RequestBody(required = false) RefundRequest request) {
        return customWigRequestService.refund(id, request != null ? request : new RefundRequest(null));
    }
}
