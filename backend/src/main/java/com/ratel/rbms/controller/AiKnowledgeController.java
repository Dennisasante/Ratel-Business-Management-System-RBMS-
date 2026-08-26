package com.ratel.rbms.controller;

import com.ratel.rbms.dto.AiKnowledgeEntryRequest;
import com.ratel.rbms.dto.AiKnowledgeEntryResponse;
import com.ratel.rbms.service.AiKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/knowledge")
public class AiKnowledgeController {

    private final AiKnowledgeService aiKnowledgeService;

    public AiKnowledgeController(AiKnowledgeService aiKnowledgeService) {
        this.aiKnowledgeService = aiKnowledgeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
    public List<AiKnowledgeEntryResponse> list() {
        return aiKnowledgeService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','SALES_PERSON','ACCOUNTANT')")
    public AiKnowledgeEntryResponse get(@PathVariable UUID id) {
        return aiKnowledgeService.get(id);
    }

    // Mutations gated the same as Settings — knowledge entries shape what
    // the AI says just as much as system instructions do.
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public ResponseEntity<AiKnowledgeEntryResponse> create(@Valid @RequestBody AiKnowledgeEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(aiKnowledgeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public AiKnowledgeEntryResponse update(@PathVariable UUID id, @Valid @RequestBody AiKnowledgeEntryRequest request) {
        return aiKnowledgeService.update(id, request);
    }

    // Soft-deactivate, not delete — matches Products/ServiceCatalogItems'
    // archive-not-delete posture (see AiKnowledgeService.deactivate).
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public AiKnowledgeEntryResponse deactivate(@PathVariable UUID id) {
        return aiKnowledgeService.deactivate(id);
    }
}
