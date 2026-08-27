package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiOverviewResponse;
import com.ratel.rbms.dto.AiSettingsResponse;
import com.ratel.rbms.dto.AiSettingsUpdateRequest;
import com.ratel.rbms.entity.AiSettings;
import com.ratel.rbms.repository.AiActionRepository;
import com.ratel.rbms.repository.AiConversationRepository;
import com.ratel.rbms.repository.AiKnowledgeEntryRepository;
import com.ratel.rbms.repository.AiSettingsRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * One settings row per business (see AiSettings' own doc comment). Reading
 * settings for a business that's never configured anything yet returns
 * built-in defaults rather than 404ing — the module being enabled is what
 * the frontend actually gates on (ModuleAccessService), not the existence
 * of a settings row.
 */
@Service
public class AiSettingsService {

    private final AiSettingsRepository aiSettingsRepository;
    private final AiConversationRepository aiConversationRepository;
    private final AiActionRepository aiActionRepository;
    private final AiKnowledgeEntryRepository aiKnowledgeEntryRepository;
    private final ModuleAccessService moduleAccessService;

    public AiSettingsService(
            AiSettingsRepository aiSettingsRepository,
            AiConversationRepository aiConversationRepository,
            AiActionRepository aiActionRepository,
            AiKnowledgeEntryRepository aiKnowledgeEntryRepository,
            ModuleAccessService moduleAccessService
    ) {
        this.aiSettingsRepository = aiSettingsRepository;
        this.aiConversationRepository = aiConversationRepository;
        this.aiActionRepository = aiActionRepository;
        this.aiKnowledgeEntryRepository = aiKnowledgeEntryRepository;
        this.moduleAccessService = moduleAccessService;
    }

    public AiSettingsResponse get() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");
        return aiSettingsRepository.findByBusinessId(businessId)
                .map(AiSettingsResponse::from)
                .orElseGet(AiSettingsResponse::defaults);
    }

    @Transactional
    public AiSettingsResponse update(AiSettingsUpdateRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        AiSettings settings = aiSettingsRepository.findByBusinessId(businessId)
                .orElseGet(() -> AiSettings.builder().businessId(businessId).build());

        settings.setActive(req.active());
        settings.setAgentName(req.agentName().trim());
        settings.setGreeting(blankToNull(req.greeting()));
        settings.setTone(blankToNull(req.tone()));
        settings.setSystemInstructions(blankToNull(req.systemInstructions()));
        settings.setHumanHandoffEnabled(req.humanHandoffEnabled());
        settings.setHumanHandoffMessage(blankToNull(req.humanHandoffMessage()));

        settings = aiSettingsRepository.save(settings);
        return AiSettingsResponse.from(settings);
    }

    public AiOverviewResponse overview() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        AiSettingsResponse settings = aiSettingsRepository.findByBusinessId(businessId)
                .map(AiSettingsResponse::from)
                .orElseGet(AiSettingsResponse::defaults);

        return new AiOverviewResponse(
                settings.active(),
                settings.agentName(),
                aiConversationRepository.countByBusinessId(businessId),
                aiConversationRepository.countByBusinessIdAndStatus(businessId, "ACTIVE"),
                aiConversationRepository.countByBusinessIdAndStatus(businessId, "ESCALATED"),
                aiActionRepository.countByBusinessId(businessId),
                aiKnowledgeEntryRepository.countByBusinessId(businessId),
                aiActionRepository.countByBusinessIdAndToolNameAndStatus(businessId, "createBooking", "SUCCEEDED")
        );
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
