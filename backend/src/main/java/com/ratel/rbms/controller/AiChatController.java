package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AiChatRequest;
import com.ratel.rbms.dto.AiChatResponse;
import com.ratel.rbms.service.AiChannelRouter;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Internal demo/test endpoint only — NOT the eventual customer-facing
// channel entry point. This is what the dashboard's Test AI panel calls;
// authenticated, business-scoped, module-gated, exactly like every other
// endpoint here. No external channel (WhatsApp/Instagram/etc.) reaches this.
//
// Goes through AiChannelRouter.routeWebDemo (Phase 3A), not
// AiChatService.chat(...) directly — WEB_DEMO now genuinely exercises the
// same channel-routing path a future external channel's webhook controller
// would, rather than the AI core being reached two different ways. The
// resulting behavior is identical; AiChatService.chat(...) itself is kept
// fully intact for existing tests/callers.
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChannelRouter aiChannelRouter;

    public AiChatController(AiChannelRouter aiChannelRouter) {
        this.aiChannelRouter = aiChannelRouter;
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiChannelRouter.routeWebDemo(request.conversationId(), request.message());
    }
}
