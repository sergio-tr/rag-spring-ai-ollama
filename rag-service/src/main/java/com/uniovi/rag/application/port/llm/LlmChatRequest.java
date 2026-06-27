package com.uniovi.rag.application.port.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-agnostic chat completion request. RAG callers supply model, messages, and sampling controls only.
 */
public record LlmChatRequest(
        String model,
        List<LlmChatMessage> messages,
        Double temperature,
        Integer timeoutMs,
        Map<String, Object> additionalParameters) {

    public LlmChatRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        additionalParameters =
                additionalParameters != null && !additionalParameters.isEmpty()
                        ? Map.copyOf(additionalParameters)
                        : Map.of();
    }

    /**
     * Typical RAG workflow shape: optional system prompt plus a single user turn.
     */
    public static LlmChatRequest of(String model, String systemPrompt, String userMessage) {
        return of(model, systemPrompt, userMessage, null, null, Map.of());
    }

    public static LlmChatRequest of(
            String model,
            String systemPrompt,
            String userMessage,
            Double temperature,
            Integer timeoutMs,
            Map<String, Object> additionalParameters) {
        var messageList = new ArrayList<LlmChatMessage>(2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messageList.add(LlmChatMessage.system(systemPrompt));
        }
        messageList.add(LlmChatMessage.user(userMessage != null ? userMessage : ""));
        return new LlmChatRequest(model, messageList, temperature, timeoutMs, additionalParameters);
    }
}
