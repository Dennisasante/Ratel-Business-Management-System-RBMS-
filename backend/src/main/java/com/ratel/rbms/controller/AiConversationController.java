package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AiConversationDetailResponse;
import com.ratel.rbms.dto.AiConversationSummaryResponse;
import com.ratel.rbms.service.AiConversationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Phase 1 is inspect-only — no omnichannel inbox, no reply-from-dashboard,
// no reassignment. Just enough to see what the AI has been doing.
@RestController
@RequestMapping("/api/ai/conversations")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class AiConversationController {

    private final AiConversationService aiConversationService;

    public AiConversationController(AiConversationService aiConversationService) {
        this.aiConversationService = aiConversationService;
    }

    @GetMapping
    public List<AiConversationSummaryResponse> list() {
        return aiConversationService.list();
    }

    @GetMapping("/{id}")
    public AiConversationDetailResponse get(@PathVariable UUID id) {
        return aiConversationService.getDetail(id);
    }
}
