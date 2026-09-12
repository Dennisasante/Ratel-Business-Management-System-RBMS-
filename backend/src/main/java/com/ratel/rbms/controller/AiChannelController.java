package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AiChannelConnectionResponse;
import com.ratel.rbms.dto.AiChannelStatusResponse;
import com.ratel.rbms.dto.ConnectWhatsAppRequest;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.service.AiChannelConnectionService;
import com.ratel.rbms.service.AiChannelStatusService;
import com.ratel.rbms.service.ConnectionParams;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talia Unified Platform, Phase 5D Stage 0 — {@code list()} remains the pre-existing read-only
 * status view (spec §26/§27, unchanged). Everything below it is new: the business-facing
 * (OWNER/MANAGER) counterpart to what was, until this stage, a Super-Admin-only surface
 * ({@code PlatformWhatsAppBindingController}, left completely intact and unmodified — Super Admin
 * retains its own management capability). WhatsApp is the only channel wired up in this stage;
 * every endpoint below is written generically over {@link AiChannel} so Instagram/Voice/Website
 * Chat need only a new request DTO + a new registered {@code ChannelConnectionInitiator}, never a
 * new controller shape.
 */
@RestController
@RequestMapping("/api/ai/channels")
@PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
public class AiChannelController {

    private final AiChannelStatusService aiChannelStatusService;
    private final AiChannelConnectionService aiChannelConnectionService;

    public AiChannelController(AiChannelStatusService aiChannelStatusService,
                                AiChannelConnectionService aiChannelConnectionService) {
        this.aiChannelStatusService = aiChannelStatusService;
        this.aiChannelConnectionService = aiChannelConnectionService;
    }

    @GetMapping
    public List<AiChannelStatusResponse> list() {
        return aiChannelStatusService.list();
    }

    @GetMapping("/whatsapp")
    public AiChannelConnectionResponse getWhatsApp() {
        return aiChannelConnectionService.getDetail(AiChannel.WHATSAPP);
    }

    // Connect (no existing binding) or reconnect/update credentials (one already exists) — same
    // endpoint either way, matching the architecture's own "connect/configure" + "reconnect/update
    // credentials" being the same underlying action (spec §3/§5).
    @PostMapping("/whatsapp/connect")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public AiChannelConnectionResponse connectWhatsApp(@Valid @RequestBody ConnectWhatsAppRequest request) {
        Map<String, String> values = new HashMap<>();
        values.put("phoneNumberId", request.phoneNumberId());
        values.put("accessToken", request.accessToken());
        values.put("whatsappBusinessAccountId", request.whatsappBusinessAccountId());
        values.put("displayName", request.displayName());
        return aiChannelConnectionService.connect(AiChannel.WHATSAPP, new ConnectionParams(values));
    }

    @PostMapping("/whatsapp/test")
    public AiChannelConnectionResponse testWhatsApp() {
        return aiChannelConnectionService.test(AiChannel.WHATSAPP);
    }

    // Also the "disconnect" action (active=false) — see AiChannelConnectionService.setActive's own
    // doc comment; there is no separate hard-delete/disconnect endpoint.
    @PatchMapping("/whatsapp/active")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public AiChannelConnectionResponse setWhatsAppActive(@RequestBody SetChannelActiveRequest request) {
        return aiChannelConnectionService.setActive(AiChannel.WHATSAPP, request.active());
    }

    public record SetChannelActiveRequest(boolean active) {
    }
}
