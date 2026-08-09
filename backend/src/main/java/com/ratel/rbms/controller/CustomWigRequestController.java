package com.ratel.rbms.controller;

import com.ratel.rbms.dto.CustomWigRequestDetailResponse;
import com.ratel.rbms.dto.CustomWigRequestResponse;
import com.ratel.rbms.dto.DeclineCustomWigRequestRequest;
import com.ratel.rbms.dto.QuoteCustomWigRequestRequest;
import com.ratel.rbms.service.CustomWigRequestService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/custom-wig-requests")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class CustomWigRequestController {

    private final CustomWigRequestService customWigRequestService;

    public CustomWigRequestController(CustomWigRequestService customWigRequestService) {
        this.customWigRequestService = customWigRequestService;
    }

    @GetMapping
    public List<CustomWigRequestResponse> list() {
        return customWigRequestService.list();
    }

    @GetMapping("/{id}")
    public CustomWigRequestDetailResponse get(@PathVariable UUID id) {
        return customWigRequestService.get(id);
    }

    @PatchMapping("/{id}/quote")
    public CustomWigRequestResponse quote(@PathVariable UUID id, @Valid @RequestBody QuoteCustomWigRequestRequest request) {
        return customWigRequestService.quote(id, request.finalPrice(), request.message());
    }

    @PatchMapping("/{id}/decline")
    public CustomWigRequestResponse decline(@PathVariable UUID id, @RequestBody DeclineCustomWigRequestRequest request) {
        return customWigRequestService.decline(id, request.message());
    }

    @PatchMapping("/{id}/accept")
    public CustomWigRequestResponse accept(@PathVariable UUID id) {
        return customWigRequestService.accept(id);
    }
}
