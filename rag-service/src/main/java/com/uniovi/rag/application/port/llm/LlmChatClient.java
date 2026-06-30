package com.uniovi.rag.application.port.llm;

import com.uniovi.rag.domain.llm.LlmProvider;

/**
 * Outbound port for provider-agnostic chat completion.
 * Implementations translate to Ollama {@code /api/chat} or OpenAI-compatible {@code /v1/chat/completions}.
 */
public interface LlmChatClient {

    /**
     * Runs a non-streaming chat completion for the given request.
     */
    LlmChatResponse chat(LlmChatRequest request);

    /** Backend kind served by this client instance. */
    LlmProvider provider();
}
