package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaNativeLlmChatClient;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaNativeLlmEmbeddingClient;
import org.springframework.stereotype.Component;

@Component
class DefaultLlmClientRegistry implements LlmClientRegistryPort {

    private final OllamaNativeLlmChatClient ollamaChatClient;
    private final OllamaNativeLlmEmbeddingClient ollamaEmbeddingClient;
    private final OpenAiCompatibleApiKeyResolver apiKeyResolver;
    private final OpenAiCompatibleChatCompletionsHttpClient chatHttpClient;
    private final OpenAiCompatibleEmbeddingsHttpClient embeddingsHttpClient;

    DefaultLlmClientRegistry(
            OllamaNativeLlmChatClient ollamaChatClient,
            OllamaNativeLlmEmbeddingClient ollamaEmbeddingClient,
            OpenAiCompatibleApiKeyResolver apiKeyResolver,
            OpenAiCompatibleChatCompletionsHttpClient chatHttpClient,
            OpenAiCompatibleEmbeddingsHttpClient embeddingsHttpClient) {
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaEmbeddingClient = ollamaEmbeddingClient;
        this.apiKeyResolver = apiKeyResolver;
        this.chatHttpClient = chatHttpClient;
        this.embeddingsHttpClient = embeddingsHttpClient;
    }

    @Override
    public LlmChatClient ollamaNativeChatClient() {
        return ollamaChatClient;
    }

    @Override
    public LlmEmbeddingClient ollamaNativeEmbeddingClient() {
        return ollamaEmbeddingClient;
    }

    @Override
    public LlmChatClient createOpenAiCompatibleChatClient(ResolvedLlmConfig config) {
        return new ResolvedConfigOpenAiCompatibleLlmChatClient(config, apiKeyResolver, chatHttpClient);
    }

    @Override
    public LlmEmbeddingClient createOpenAiCompatibleEmbeddingClient(ResolvedLlmConfig config) {
        return new ResolvedConfigOpenAiCompatibleLlmEmbeddingClient(config, apiKeyResolver, embeddingsHttpClient);
    }
}
