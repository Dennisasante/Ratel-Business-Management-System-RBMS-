package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiChannelStatusResponse;
import com.ratel.rbms.entity.AiChannelBinding;
import com.ratel.rbms.entity.enums.AiChannel;
import com.ratel.rbms.repository.AiChannelBindingRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the dashboard's read-only "Channels" section — deliberately just a
 * status list, not a management API (spec §26/§27): no create/edit/delete
 * of a channel binding is exposed here (WhatsApp bindings are Super-Admin-
 * configured only, see PlatformWhatsAppBindingController), and no fake
 * "Connect" button exists on the frontend for a channel this phase doesn't
 * implement. Every channel besides WEB_DEMO/WHATSAPP always reports "not
 * connected," because nothing creates a binding for them yet.
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

        Map<AiChannel, AiChannelBinding> bindingsByChannel = new java.util.HashMap<>();
        for (AiChannelBinding b : aiChannelBindingRepository.findAllByBusinessId(businessId)) {
            bindingsByChannel.put(b.getChannel(), b);
        }

        List<AiChannelStatusResponse> result = new ArrayList<>();
        for (AiChannel channel : AiChannel.values()) {
            if (channel == AiChannel.WEB_DEMO) {
                result.add(AiChannelStatusResponse.webDemo());
                continue;
            }

            AiChannelBinding binding = bindingsByChannel.get(channel);
            if (binding == null) {
                result.add(AiChannelStatusResponse.notImplemented(channel.name(), label(channel)));
                continue;
            }

            boolean configured = binding.getCredentialsEncrypted() != null && !binding.getCredentialsEncrypted().isBlank();
            boolean connected = configured && binding.isActive();
            String statusMessage = !configured
                    ? "Not configured yet"
                    : binding.isActive() ? "Connected" : "Configured but inactive";
            result.add(new AiChannelStatusResponse(
                    channel.name(), label(channel), connected, statusMessage, binding.isActive(),
                    binding.getExternalAccountId(), binding.getDisplayName(), binding.getUpdatedAt()));
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
