package com.ratel.rbms.service;

import java.util.List;

/**
 * One turn in the conversation as sent to/from the LLM provider. Role is
 * "system"/"user"/"assistant"/"tool" (OpenAI's own role names — deliberately
 * not reusing AiMessage's ROLE constants directly since USER/ASSISTANT/
 * SYSTEM/TOOL there are upper-case persistence values, these are the exact
 * lower-case strings the provider API expects).
 *
 * toolCalls is only set on an assistant message that requested tool(s);
 * toolCallId is only set on a "tool" role message replying to one specific
 * call.
 */
public record AiProviderMessage(String role, String content, String toolCallId, List<AiToolCall> toolCalls) {

    public static AiProviderMessage of(String role, String content) {
        return new AiProviderMessage(role, content, null, null);
    }

    public static AiProviderMessage toolResult(String toolCallId, String content) {
        return new AiProviderMessage("tool", content, toolCallId, null);
    }

    public static AiProviderMessage assistantToolCalls(List<AiToolCall> toolCalls) {
        return new AiProviderMessage("assistant", null, null, toolCalls);
    }
}
