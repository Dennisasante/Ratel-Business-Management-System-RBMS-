package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiKnowledgeEntryRequest;
import com.ratel.rbms.dto.AiKnowledgeEntryResponse;
import com.ratel.rbms.entity.AiKnowledgeEntry;
import com.ratel.rbms.exception.ApiException;
import com.ratel.rbms.repository.AiKnowledgeEntryRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AiKnowledgeService {

    private final AiKnowledgeEntryRepository aiKnowledgeEntryRepository;
    private final ModuleAccessService moduleAccessService;

    public AiKnowledgeService(AiKnowledgeEntryRepository aiKnowledgeEntryRepository, ModuleAccessService moduleAccessService) {
        this.aiKnowledgeEntryRepository = aiKnowledgeEntryRepository;
        this.moduleAccessService = moduleAccessService;
    }

    public List<AiKnowledgeEntryResponse> list() {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");
        return aiKnowledgeEntryRepository.findAllByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(AiKnowledgeEntryResponse::from)
                .toList();
    }

    public AiKnowledgeEntryResponse get(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");
        return AiKnowledgeEntryResponse.from(getOwned(id, businessId));
    }

    @Transactional
    public AiKnowledgeEntryResponse create(AiKnowledgeEntryRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        AiKnowledgeEntry entry = AiKnowledgeEntry.builder()
                .businessId(businessId)
                .title(req.title().trim())
                .content(req.content().trim())
                .category(req.category() == null || req.category().isBlank() ? "OTHER" : req.category().trim())
                .active(req.active())
                .build();
        entry = aiKnowledgeEntryRepository.save(entry);
        // @CreationTimestamp/@UpdateTimestamp aren't populated on the Java
        // object until Hibernate actually flushes — without this the
        // response immediately after create() reads back null timestamps
        // (matches the same fix already applied for CustomWigRequest's
        // generated requestNumber).
        aiKnowledgeEntryRepository.flush();
        return AiKnowledgeEntryResponse.from(entry);
    }

    @Transactional
    public AiKnowledgeEntryResponse update(UUID id, AiKnowledgeEntryRequest req) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        AiKnowledgeEntry entry = getOwned(id, businessId);
        entry.setTitle(req.title().trim());
        entry.setContent(req.content().trim());
        entry.setCategory(req.category() == null || req.category().isBlank() ? "OTHER" : req.category().trim());
        entry.setActive(req.active());
        entry = aiKnowledgeEntryRepository.save(entry);
        aiKnowledgeEntryRepository.flush();
        return AiKnowledgeEntryResponse.from(entry);
    }

    // Deactivate rather than delete — matches Products/ServiceCatalogItems'
    // own archive-not-delete posture, and here it also means a past AI
    // conversation's transcript doesn't reference retrieval context that
    // silently vanished.
    @Transactional
    public AiKnowledgeEntryResponse deactivate(UUID id) {
        UUID businessId = TenantContext.getBusinessId();
        moduleAccessService.requireModule(businessId, "AI");

        AiKnowledgeEntry entry = getOwned(id, businessId);
        entry.setActive(false);
        entry = aiKnowledgeEntryRepository.save(entry);
        return AiKnowledgeEntryResponse.from(entry);
    }

    // Used by AiToolService's knowledge-retrieval step — no module check
    // here since the caller (AiChatService) has already gated the whole
    // chat turn on the AI module before ever reaching a tool.
    List<AiKnowledgeEntry> listActiveForBusiness(UUID businessId) {
        return aiKnowledgeEntryRepository.findAllByBusinessIdAndActiveTrueOrderByCreatedAtDesc(businessId);
    }

    private AiKnowledgeEntry getOwned(UUID id, UUID businessId) {
        return aiKnowledgeEntryRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Knowledge entry not found."));
    }
}
