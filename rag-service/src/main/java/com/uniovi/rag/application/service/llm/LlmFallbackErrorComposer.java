package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;

/** Static user-facing fallback messages that respect the effective LLM provider (BL-011). */
public final class LlmFallbackErrorComposer {

    private LlmFallbackErrorComposer() {}

    public static String connectivityUnavailable(ResolvedLlmConfig config) {
        if (config != null && config.chatProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            return "The AI inference service is unavailable. Please try again once the configured LLM API is reachable.";
        }
        return "The AI inference service is unavailable. Please try again once Ollama is running and reachable.";
    }

    public static String modelNotInstalled(ResolvedLlmConfig config) {
        if (config != null && config.chatProvider() == LlmProvider.OPENAI_COMPATIBLE) {
            return "A required LLM model is not available on the configured API. Verify the model id or proxy configuration.";
        }
        return "A required Ollama model is not installed. Pull the chat and embedding models on the Ollama host "
                + "(ollama pull …) or wait for automatic pull at startup.";
    }

    public static String genericApology() {
        return "I'm sorry, an error occurred while processing your query. Please try again.";
    }
}
