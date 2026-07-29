package com.ratel.rbms.service;

import com.ratel.rbms.dto.PlatformBillingSettingsRequest;
import com.ratel.rbms.dto.PlatformBillingSettingsResponse;
import com.ratel.rbms.entity.PlatformBillingSettings;
import com.ratel.rbms.repository.PlatformBillingSettingsRepository;
import org.springframework.stereotype.Service;

@Service
public class PlatformBillingSettingsService {

    private final PlatformBillingSettingsRepository platformBillingSettingsRepository;

    public PlatformBillingSettingsService(PlatformBillingSettingsRepository platformBillingSettingsRepository) {
        this.platformBillingSettingsRepository = platformBillingSettingsRepository;
    }

    public PlatformBillingSettingsResponse get() {
        return PlatformBillingSettingsResponse.from(platformBillingSettingsRepository.findFirstByOrderByUpdatedAtDesc());
    }

    public PlatformBillingSettingsResponse update(PlatformBillingSettingsRequest req) {
        PlatformBillingSettings settings = platformBillingSettingsRepository.findFirstByOrderByUpdatedAtDesc();
        settings.setTrialDays(req.trialDays());
        settings.setUsdDisplayRate(req.usdDisplayRate());
        return PlatformBillingSettingsResponse.from(platformBillingSettingsRepository.save(settings));
    }
}
