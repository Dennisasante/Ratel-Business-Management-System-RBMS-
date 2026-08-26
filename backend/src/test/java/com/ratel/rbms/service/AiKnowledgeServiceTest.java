package com.ratel.rbms.service;

import com.ratel.rbms.dto.AiKnowledgeEntryRequest;
import com.ratel.rbms.dto.AiKnowledgeEntryResponse;
import com.ratel.rbms.entity.AiKnowledgeEntry;
import com.ratel.rbms.entity.Business;
import com.ratel.rbms.entity.enums.Industry;
import com.ratel.rbms.repository.BusinessRepository;
import com.ratel.rbms.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance Test 6 from the spec, specifically for the knowledge base:
 * Business A's AI must never retrieve Business B's knowledge. Also covers
 * the plain create/list/deactivate CRUD and the "supply every active entry,
 * no vector DB" retrieval approach for Phase 1.
 */
@SpringBootTest
@Transactional
class AiKnowledgeServiceTest {

    @Autowired
    private AiKnowledgeService aiKnowledgeService;
    @Autowired
    private BusinessRepository businessRepository;

    private Business businessA;
    private Business businessB;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Business aiEnabledBusiness(String label) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Business business = Business.builder()
                .name(label + " " + unique)
                .slug(label.toLowerCase().replace(" ", "-") + "-" + unique)
                .industry(Industry.SALON)
                .currency("GHS")
                .build();
        business.setEnabledModules(concat(business.getEnabledModules(), "AI"));
        return businessRepository.save(business);
    }

    @Test
    void oneBusinessNeverSeesAnotherBusinesssKnowledge() {
        businessA = aiEnabledBusiness("Knowledge Business A");
        businessB = aiEnabledBusiness("Knowledge Business B");

        TenantContext.setBusinessId(businessA.getId());
        aiKnowledgeService.create(new AiKnowledgeEntryRequest("A's secret policy", "Only A should ever see this.", "POLICY", true));

        TenantContext.setBusinessId(businessB.getId());
        aiKnowledgeService.create(new AiKnowledgeEntryRequest("B's own FAQ", "Only B should ever see this.", "FAQ", true));

        List<AiKnowledgeEntryResponse> bList = aiKnowledgeService.list();
        assertEquals(1, bList.size());
        assertEquals("B's own FAQ", bList.get(0).title());

        TenantContext.setBusinessId(businessA.getId());
        List<AiKnowledgeEntryResponse> aList = aiKnowledgeService.list();
        assertEquals(1, aList.size());
        assertEquals("A's secret policy", aList.get(0).title());

        // Same isolation for the internal retrieval path AiChatService uses
        // to build the system prompt — this is the one that actually
        // matters for what the model gets to see.
        List<AiKnowledgeEntry> retrievalForA = aiKnowledgeService.listActiveForBusiness(businessA.getId());
        assertEquals(1, retrievalForA.size());
        assertTrue(retrievalForA.get(0).getContent().contains("Only A"));
    }

    @Test
    void deactivatedEntriesAreExcludedFromRetrievalButNotFromTheManagementList() {
        businessA = aiEnabledBusiness("Deactivate Test Business");
        TenantContext.setBusinessId(businessA.getId());

        AiKnowledgeEntryResponse created = aiKnowledgeService.create(
                new AiKnowledgeEntryRequest("Old promo", "20% off in July.", "POLICY", true));
        aiKnowledgeService.deactivate(created.id());

        assertEquals(1, aiKnowledgeService.list().size(), "Deactivated entries still show in the management list");
        assertTrue(aiKnowledgeService.listActiveForBusiness(businessA.getId()).isEmpty(),
                "But must never be handed to the model as active knowledge");
    }

    private List<String> concat(List<String> base, String extra) {
        var copy = new java.util.ArrayList<>(base);
        copy.add(extra);
        return copy;
    }
}
