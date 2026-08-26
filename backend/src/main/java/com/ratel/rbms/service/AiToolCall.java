package com.ratel.rbms.service;

/** One tool invocation the model requested — id is the provider's own call id, needed to match the tool result back to it. */
public record AiToolCall(String id, String name, String argumentsJson) {
}
