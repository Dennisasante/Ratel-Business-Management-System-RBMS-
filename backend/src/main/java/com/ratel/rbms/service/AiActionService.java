package com.ratel.rbms.service;

import com.ratel.rbms.entity.AiAction;
import com.ratel.rbms.repository.AiActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Dedicated AI tool-execution audit trail (see AiAction's own doc comment
 * for why this exists alongside, not instead of, ActivityLogService).
 * Write-only/passive, same philosophy as PaymentTransactionService.record()
 * — called from the exact point a tool actually runs, so it can't disagree
 * with what really happened.
 */
@Service
public class AiActionService {

    private final AiActionRepository aiActionRepository;

    public AiActionService(AiActionRepository aiActionRepository) {
        this.aiActionRepository = aiActionRepository;
    }

    @Transactional
    public AiAction started(UUID businessId, UUID conversationId, UUID messageId, String toolName, String argumentsJson) {
        return aiActionRepository.save(AiAction.builder()
                .businessId(businessId)
                .conversationId(conversationId)
                .messageId(messageId)
                .toolName(toolName)
                .argumentsJson(argumentsJson)
                .status("STARTED")
                .build());
    }

    @Transactional
    public void succeeded(AiAction action, String resultJson, String resultingEntityType, UUID resultingEntityId) {
        action.setStatus("SUCCEEDED");
        action.setResultJson(resultJson);
        action.setResultingEntityType(resultingEntityType);
        action.setResultingEntityId(resultingEntityId);
        aiActionRepository.save(action);
    }

    @Transactional
    public void failed(AiAction action, String errorMessage) {
        action.setStatus("FAILED");
        action.setResultJson(errorMessage);
        aiActionRepository.save(action);
    }

    // A tool name the model asked for that isn't in the registered
    // allow-list — never executed at all, logged for visibility into what
    // the model attempted rather than silently dropped.
    @Transactional
    public void blocked(UUID businessId, UUID conversationId, UUID messageId, String toolName, String argumentsJson) {
        aiActionRepository.save(AiAction.builder()
                .businessId(businessId)
                .conversationId(conversationId)
                .messageId(messageId)
                .toolName(toolName)
                .argumentsJson(argumentsJson)
                .status("BLOCKED")
                .build());
    }
}
