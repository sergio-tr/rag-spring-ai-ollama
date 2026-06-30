package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.port.llm.LlmTokenUsage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** Maps Spring AI {@link ChatResponse} into port {@link LlmChatResponse}. */
final class OllamaLlmChatResponseMapper {

    private OllamaLlmChatResponseMapper() {}

    static LlmChatResponse toPortResponse(ChatResponse response, String requestedModel) {
        String content = "";
        String finishReason = null;
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            String text = response.getResult().getOutput().getText();
            content = text != null ? text : "";
            ChatGenerationMetadata generationMetadata = response.getResult().getMetadata();
            if (generationMetadata != null) {
                finishReason = generationMetadata.getFinishReason();
            }
        }
        LlmTokenUsage usage = extractUsage(response);
        Map<String, Object> rawMetadata = extractRawMetadata(response);
        String model = requestedModel;
        if (response != null && response.getMetadata() != null && response.getMetadata().getModel() != null) {
            model = response.getMetadata().getModel();
        }
        return new LlmChatResponse(content, model, finishReason, usage, rawMetadata);
    }

    private static LlmTokenUsage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        return new LlmTokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private static Map<String, Object> extractRawMetadata(ChatResponse response) {
        if (response == null) {
            return Map.of();
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        ChatResponseMetadata metadata = response.getMetadata();
        if (metadata != null) {
            if (metadata.getId() != null) {
                raw.put("id", metadata.getId());
            }
            if (metadata.getModel() != null) {
                raw.put("model", metadata.getModel());
            }
        }
        Generation result = response.getResult();
        if (result != null && result.getMetadata() != null && result.getMetadata().getFinishReason() != null) {
            raw.put("finishReason", result.getMetadata().getFinishReason());
        }
        return raw.isEmpty() ? Map.of() : Map.copyOf(raw);
    }
}
