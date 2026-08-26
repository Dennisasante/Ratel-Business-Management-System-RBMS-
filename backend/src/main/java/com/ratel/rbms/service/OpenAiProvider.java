package com.ratel.rbms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ratel.rbms.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Calls OpenAI's Chat Completions API directly via Spring's RestClient,
 * rather than the openai-java SDK — same "direct REST call, no third-party
 * SDK" pattern PaystackService already established for external API
 * integrations in this codebase (see that class's own client() method).
 * This keeps the dependency footprint unchanged and avoids taking on a new
 * library's own version-compatibility surface for what is, underneath, a
 * single well-documented JSON endpoint.
 *
 * Never logs or returns the API key. If OPENAI_API_KEY isn't set,
 * isConfigured() is false and chat() is never called — mirrors
 * EmailService/PushNotificationService's "silently unusable, not a crash"
 * posture when a third-party credential is missing.
 */
@Service
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final String BASE_URL = "https://api.openai.com/v1";

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAiProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.openai-api-key}") String apiKey,
            @Value("${app.ai.openai-model}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public AiProviderResult chat(String systemPrompt, List<AiProviderMessage> conversation, List<AiToolDefinition> tools) {
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Tallia AI isn't set up on this server yet.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", buildMessages(systemPrompt, conversation));
        if (!tools.isEmpty()) {
            body.set("tools", buildTools(tools));
            body.put("tool_choice", "auto");
        }

        JsonNode response;
        try {
            response = client()
                    .post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    // OpenAI answers a rejected request (bad key, rate limit, model
                    // not available) with a real `error.message` in the body — read
                    // it below instead of letting the default handler discard it.
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.error("OpenAI chat completion call failed", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach the AI provider. Please try again.");
        }

        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach the AI provider. Please try again.");
        }
        if (response.has("error")) {
            String message = response.path("error").path("message").asText("The AI provider rejected this request.");
            log.error("OpenAI returned an error: {}", message);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI provider couldn't process that. Please try again.");
        }

        JsonNode message = response.path("choices").path(0).path("message");
        JsonNode toolCallsNode = message.path("tool_calls");

        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<AiToolCall> calls = new ArrayList<>();
            for (JsonNode call : toolCallsNode) {
                calls.add(new AiToolCall(
                        call.path("id").asText(null),
                        call.path("function").path("name").asText(null),
                        call.path("function").path("arguments").asText("{}")
                ));
            }
            return new AiProviderResult(null, calls);
        }

        String content = message.path("content").asText("");
        return new AiProviderResult(content, List.of());
    }

    private ArrayNode buildMessages(String systemPrompt, List<AiProviderMessage> conversation) {
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", systemPrompt);

        for (AiProviderMessage m : conversation) {
            ObjectNode node = messages.addObject();
            node.put("role", m.role());
            if (m.content() != null) {
                node.put("content", m.content());
            } else {
                node.putNull("content");
            }
            if (m.toolCallId() != null) {
                node.put("tool_call_id", m.toolCallId());
            }
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                ArrayNode toolCalls = node.putArray("tool_calls");
                for (AiToolCall call : m.toolCalls()) {
                    ObjectNode callNode = toolCalls.addObject();
                    callNode.put("id", call.id());
                    callNode.put("type", "function");
                    ObjectNode function = callNode.putObject("function");
                    function.put("name", call.name());
                    function.put("arguments", call.argumentsJson());
                }
            }
        }
        return messages;
    }

    private ArrayNode buildTools(List<AiToolDefinition> tools) {
        ArrayNode array = objectMapper.createArrayNode();
        for (AiToolDefinition tool : tools) {
            ObjectNode toolNode = array.addObject();
            toolNode.put("type", "function");
            ObjectNode function = toolNode.putObject("function");
            function.put("name", tool.name());
            function.put("description", tool.description());
            try {
                function.set("parameters", objectMapper.readTree(tool.parametersJsonSchema()));
            } catch (Exception e) {
                throw new IllegalStateException("Malformed tool parameter schema for " + tool.name(), e);
            }
        }
        return array;
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }
}
