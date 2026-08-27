package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AiChannelStatusResponse;
import com.ratel.rbms.service.AiChannelStatusService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only channel status (spec §26/§27) — no channel-binding CRUD exposed
// here or anywhere else yet. Same role set and module gate as every other
// AI endpoint; there is no channel-specific exception to ModuleAccessService.
@RestController
@RequestMapping("/api/ai/channels")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class AiChannelController {

    private final AiChannelStatusService aiChannelStatusService;

    public AiChannelController(AiChannelStatusService aiChannelStatusService) {
        this.aiChannelStatusService = aiChannelStatusService;
    }

    @GetMapping
    public List<AiChannelStatusResponse> list() {
        return aiChannelStatusService.list();
    }
}
