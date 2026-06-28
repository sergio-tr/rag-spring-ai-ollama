package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.application.port.llm.LlmChatMessage;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.port.llm.LlmChatRole;
import com.uniovi.rag.application.port.llm.LlmTokenUsage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maps between port chat DTOs and OpenAI-compatible JSON payloads. */
final class OpenAiCompatibleChatMapper {

    private OpenAiCompatibleChatMapper() {}

    static OpenAiChatCompletionRequest toApiRequest(LlmChatRequest request) {
        List<OpenAiChatMessageDto> messages = new ArrayList<>(request.messages().size());
        for (LlmChatMessage message : request.messages()) {
            messages.add(new OpenAiChatMessageDto(toApiRole(message.role()), message.content()));
        }
        return new OpenAiChatCompletionRequest(request.model(), messages, request.temperature());
    }

    static LlmChatResponse toPortResponse(OpenAiChatCompletionResponse response, String requestedModel) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw OpenAiCompatibleLlmException.invalidResponse("choices array is missing or empty");
        }
        OpenAiChatChoiceDto first = response.choices().getFirst();
        if (first.message() == null) {
            throw OpenAiCompatibleLlmException.invalidResponse("first choice has no message");
        }
        String content = first.message().content() != null ? first.message().content() : "";
        String model = response.model() != null && !response.model().isBlank() ? response.model() : requestedModel;
        LlmTokenUsage usage = null;
        if (response.usage() != null) {
            usage =
                    new LlmTokenUsage(
                            response.usage().promptTokens(),
                            response.usage().completionTokens(),
                            response.usage().totalTokens());
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        if (first.finishReason() != null) {
            raw.put("finish_reason", first.finishReason());
        }
        if (response.usage() != null) {
            raw.put("usage", Map.of(
                    "prompt_tokens", response.usage().promptTokens(),
                    "completion_tokens", response.usage().completionTokens(),
                    "total_tokens", response.usage().totalTokens()));
        }
        return new LlmChatResponse(content, model, first.finishReason(), usage, raw.isEmpty() ? Map.of() : Map.copyOf(raw));
    }

    private static String toApiRole(LlmChatRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    static OpenAiCompatibleLlmException mapHttpError(int statusCode, String body, String completionsUrl) {
        String snippet = truncate(body, 400);
        if (statusCode == 401 || statusCode == 403) {
            return OpenAiCompatibleLlmException.unauthorized(statusCode);
        }
        if (statusCode == 404) {
            return OpenAiCompatibleLlmException.endpointNotFound(completionsUrl, statusCode);
        }
        if (statusCode == 400 && looksLikeModelError(body)) {
            return OpenAiCompatibleLlmException.invalidModel(snippet);
        }
        return OpenAiCompatibleLlmException.httpError(statusCode, snippet);
    }

    private static boolean looksLikeModelError(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("model")
                && (lower.contains("not found")
                        || lower.contains("does not exist")
                        || lower.contains("invalid")
                        || lower.contains("unknown"));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
