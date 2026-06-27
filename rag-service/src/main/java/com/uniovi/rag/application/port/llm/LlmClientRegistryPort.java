package com.uniovi.rag.application.port.llm;

import com.uniovi.rag.domain.llm.ResolvedLlmConfig;

/**
 * Supplies provider-specific {@link LlmChatClient} and {@link LlmEmbeddingClient} instances.
 * Implementations live in infrastructure; selection logic stays in {@code LlmClientResolver}.
 */
public interface LlmClientRegistryPort {

    LlmChatClient ollamaNativeChatClient();

    LlmEmbeddingClient ollamaNativeEmbeddingClient();

    /**
     * Builds an OpenAI-compatible chat client bound to the given resolved configuration (per-user endpoint, env ref).
     */
    LlmChatClient createOpenAiCompatibleChatClient(ResolvedLlmConfig config);
}
