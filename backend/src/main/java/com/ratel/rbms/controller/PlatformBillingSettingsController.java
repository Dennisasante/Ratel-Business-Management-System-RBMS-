package com.ratel.rbms.controller;

import com.ratel.rbms.dto.PlatformBillingSettingsRequest;
import com.ratel.rbms.dto.PlatformBillingSettingsResponse;
import com.ratel.rbms.service.PlatformBillingSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/billing-settings")
public class PlatformBillingSettingsController {

    private final PlatformBillingSettingsService platformBillingSettingsService;

    public PlatformBillingSettingsController(PlatformBillingSettingsService platformBillingSettingsService) {
        this.platformBillingSettingsService = platformBillingSettingsService;
    }

    @GetMapping
    public PlatformBillingSettingsResponse get() {
        return platformBillingSettingsService.get();
    }

    @PutMapping
    public PlatformBillingSettingsResponse update(@Valid @RequestBody PlatformBillingSettingsRequest request) {
        return platformBillingSettingsService.update(request);
    }
}
