package com.ratel.rbms.service;

/**
 * One tool the model is told it may call, in OpenAI function-calling shape.
 * {@code parametersJsonSchema} is a raw JSON Schema object (as a string,
 * built by AiToolRegistry) describing the arguments — the provider layer
 * doesn't interpret it, just forwards it.
 */
public record AiToolDefinition(String name, String description, String parametersJsonSchema) {
}
