package com.uniovi.rag.domain.llm;

import java.util.Locale;

/** Provider/backend parameter capability matrix aligned with product UI catalog. */
public final class LlmProviderParameterSupport {

    private LlmProviderParameterSupport() {}

    public static boolean isSupported(LlmRoutingBackend backend, LlmGenerationParameterId parameter, String modelId) {
        if (backend == LlmRoutingBackend.OPENAI_COMPATIBLE_API) {
            return true;
        }
        return switch (parameter) {
            case TEMPERATURE, TOP_P, SEED, MAX_TOKENS -> true;
            case THINK -> supportsThinkParameter(modelId);
            case PRESENCE_PENALTY, FREQUENCY_PENALTY, RESPONSE_FORMAT, STOP -> false;
        };
    }

    /**
     * Thinking models routed via LiteLLM/Ollama require an explicit {@code think=false} to populate assistant content.
     */
    public static boolean supportsThinkParameter(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String lower = modelId.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("embedding") || lower.contains("embed")) {
            return false;
        }
        return lower.contains("qwen3")
                || lower.contains("deepseek-r1")
                || lower.contains("gpt-oss");
    }

    public static boolean isOllamaStyleModelName(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String trimmed = modelId.trim();
        return trimmed.contains(":") && !trimmed.contains("hf.co");
    }
}
