package com.ratel.rbms.service;

import java.util.List;

/** Either a final assistant message (content set, toolCalls empty) or a request to run tool(s) (content null, toolCalls set). */
public record AiProviderResult(String content, List<AiToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
