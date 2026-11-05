package com.ratel.rbms.controller;

import com.ratel.rbms.dto.SmsAudienceRowResponse;
import com.ratel.rbms.dto.SmsCampaignDetailResponse;
import com.ratel.rbms.dto.SmsCampaignPreviewResponse;
import com.ratel.rbms.dto.SmsCampaignRequest;
import com.ratel.rbms.dto.SmsCampaignSummaryResponse;
import com.ratel.rbms.service.SmsCampaignService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Mass-texting spends real money and reaches every customer, so it is an Owner/Manager job.
@RestController
@RequestMapping("/api/sms/campaigns")
@PreAuthorize("hasAnyRole('OWNER','MANAGER')")
public class SmsCampaignController {

    private final SmsCampaignService campaignService;

    public SmsCampaignController(SmsCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public List<SmsCampaignSummaryResponse> list() {
        return campaignService.list();
    }

    @GetMapping("/audience")
    public List<SmsAudienceRowResponse> audience() {
        return campaignService.audience();
    }

    @PostMapping("/preview")
    public SmsCampaignPreviewResponse preview(@Valid @RequestBody SmsCampaignRequest request) {
        return campaignService.preview(request);
    }

    @PostMapping
    public SmsCampaignSummaryResponse create(@Valid @RequestBody SmsCampaignRequest request) {
        return campaignService.create(request);
    }

    @GetMapping("/{id}")
    public SmsCampaignDetailResponse detail(@PathVariable UUID id) {
        return campaignService.detail(id);
    }

    @PostMapping("/{id}/cancel")
    public SmsCampaignSummaryResponse cancel(@PathVariable UUID id) {
        return campaignService.cancel(id);
    }
}
