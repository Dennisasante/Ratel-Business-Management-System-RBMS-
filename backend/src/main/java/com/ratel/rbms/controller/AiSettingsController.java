package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AiOverviewResponse;
import com.ratel.rbms.dto.AiSettingsResponse;
import com.ratel.rbms.dto.AiSettingsUpdateRequest;
import com.ratel.rbms.service.AiSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    public AiSettingsController(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
    public AiOverviewResponse overview() {
        return aiSettingsService.overview();
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
    public AiSettingsResponse getSettings() {
        return aiSettingsService.get();
    }

    // Narrower than most mutation endpoints in this codebase — a normal
    // staff member should not be able to change the AI's system
    // instructions or turn it off, per spec.
    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public AiSettingsResponse updateSettings(@Valid @RequestBody AiSettingsUpdateRequest request) {
        return aiSettingsService.update(request);
    }
}
