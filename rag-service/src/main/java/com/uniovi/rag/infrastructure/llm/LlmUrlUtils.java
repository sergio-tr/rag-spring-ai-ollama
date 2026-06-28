package com.uniovi.rag.infrastructure.llm;

/** Shared URL helpers for LLM HTTP clients. */
public final class LlmUrlUtils {

    private LlmUrlUtils() {}

    public static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public static String openAiChatCompletionsUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/v1/chat/completions";
    }

    public static String openAiEmbeddingsUrl(String baseUrl) {
        return stripTrailingSlash(baseUrl) + "/v1/embeddings";
    }
}
