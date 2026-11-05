package com.ratel.rbms.controller;

import com.ratel.rbms.dto.SmsBundleRequest;
import com.ratel.rbms.dto.SmsPlatformBundleResponse;
import com.ratel.rbms.dto.SmsPlatformSettingsRequest;
import com.ratel.rbms.dto.SmsProviderKeyRequest;
import com.ratel.rbms.dto.SmsCreditAdjustRequest;
import com.ratel.rbms.dto.SmsPlatformAccountResponse;
import com.ratel.rbms.dto.SmsPlatformOverviewResponse;
import com.ratel.rbms.dto.SmsSenderUpdateRequest;
import com.ratel.rbms.service.SmsPlatformService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// /api/platform/** is already SUPER_ADMIN-only in SecurityConfig.
@RestController
@RequestMapping("/api/platform/sms")
public class PlatformSmsController {

    private final SmsPlatformService smsPlatformService;

    public PlatformSmsController(SmsPlatformService smsPlatformService) {
        this.smsPlatformService = smsPlatformService;
    }

    @GetMapping("/overview")
    public SmsPlatformOverviewResponse overview() {
        return smsPlatformService.overview();
    }

    @PutMapping("/settings")
    public SmsPlatformOverviewResponse updateSettings(@Valid @RequestBody SmsPlatformSettingsRequest request) {
        smsPlatformService.updateSettings(currentAdminId(), request);
        return smsPlatformService.overview();
    }

    @PutMapping("/provider-key")
    public SmsPlatformOverviewResponse updateProviderKey(@Valid @RequestBody SmsProviderKeyRequest request) {
        smsPlatformService.updateProviderKey(currentAdminId(), request.apiKey());
        return smsPlatformService.overview();
    }

    @GetMapping("/bundles")
    public List<SmsPlatformBundleResponse> bundles() {
        return smsPlatformService.listBundles();
    }

    @PostMapping("/bundles")
    public SmsPlatformBundleResponse createBundle(@Valid @RequestBody SmsBundleRequest request) {
        return smsPlatformService.createBundle(currentAdminId(), request);
    }

    @PutMapping("/bundles/{id}")
    public SmsPlatformBundleResponse updateBundle(@PathVariable UUID id, @Valid @RequestBody SmsBundleRequest request) {
        return smsPlatformService.updateBundle(currentAdminId(), id, request);
    }

    @GetMapping("/accounts")
    public List<SmsPlatformAccountResponse> accounts() {
        return smsPlatformService.accounts();
    }

    @PutMapping("/accounts/{businessId}/sender")
    public SmsPlatformAccountResponse updateSender(@PathVariable UUID businessId, @Valid @RequestBody SmsSenderUpdateRequest request) {
        return smsPlatformService.updateSender(currentAdminId(), businessId, request);
    }

    @PostMapping("/accounts/{businessId}/verify-sender")
    public SmsPlatformAccountResponse verifySender(@PathVariable UUID businessId) {
        return smsPlatformService.verifySender(currentAdminId(), businessId);
    }

    @PostMapping("/accounts/{businessId}/adjust")
    public SmsPlatformAccountResponse adjust(@PathVariable UUID businessId, @Valid @RequestBody SmsCreditAdjustRequest request) {
        return smsPlatformService.adjustCredits(currentAdminId(), businessId, request);
    }

    private UUID currentAdminId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
