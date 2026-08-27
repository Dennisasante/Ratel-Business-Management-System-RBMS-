package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiChannelStatusResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Backs the dashboard's read-only "Channels" section — deliberately just a
 * status list, not a management API (spec §26/§27): no create/edit/delete
 * of a channel binding is exposed here, and no fake "Connect" button exists
 * on the frontend for a channel this phase doesn't implement. Every channel
 * besides WEB_DEMO always reports "not connected" today, because nothing in
 * Phase 3A ever creates a real AiChannelBinding row.
 */
@Service
public class AiChannelStatusService {

    private final ModuleAccessService moduleAccessService;
    private final AiChannelBindingRepository aiChannelBindingRepository;

    public AiChannelStatusService(ModuleAccessService moduleAccessService, AiChannelBindingRepository aiChannelBindingRepository) {
        this.moduleAccessService = moduleAccessService;
        this.aiChannelBindingRepository = aiChannelBindingRepository;
    }

    public List<AiChannelStatusResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        List<AiChannelBinding> bindings = aiChannelBindingRepository.findAllByBusinessId(businessId);

        List<AiChannelStatusResponse> result = new ArrayList<>();
        for (AiChannel channel : AiChannel.values()) {
            if (channel == AiChannel.WEB_DEMO) {
                result.add(new AiChannelStatusResponse("WEB_DEMO", "Web Demo", true, "Connected"));
                continue;
            }
            boolean connected = bindings.stream().anyMatch(b -> b.getChannel() == channel && b.isActive());
            result.add(new AiChannelStatusResponse(channel.name(), label(channel), connected,
                    connected ? "Connected" : "Not connected — not yet implemented"));
        }
        return result;
    }

    private String label(AiChannel channel) {
        return switch (channel) {
            case WEB_DEMO -> "Web Demo";
            case WHATSAPP -> "WhatsApp";
            case INSTAGRAM -> "Instagram";
            case FACEBOOK -> "Facebook Messenger";
            case PHONE -> "Phone";
            case SMS -> "SMS";
            case EMAIL -> "Email";
        };
    }
}
