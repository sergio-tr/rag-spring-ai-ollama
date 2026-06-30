package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Minimal {@code POST /v1/embeddings} probe for readiness. Does not call Ollama or {@code /v1/models}.
 */
@Component
public class OpenAiCompatibleEmbeddingHealthProbe {

    private final ResolvedLlmConfigResolver configResolver;
    private final OpenAiCompatibleApiKeyResolver apiKeyResolver;
    private final OpenAiCompatibleEmbeddingsHttpClient httpClient;

    public OpenAiCompatibleEmbeddingHealthProbe(
            ResolvedLlmConfigResolver configResolver,
            OpenAiCompatibleApiKeyResolver apiKeyResolver,
            OpenAiCompatibleEmbeddingsHttpClient httpClient) {
        this.configResolver = configResolver;
        this.apiKeyResolver = apiKeyResolver;
        this.httpClient = httpClient;
    }

    /**
     * @return true when the proxy returns at least one embedding vector
     */
    public boolean healthCheckViaEmbeddings(int timeoutMs) {
        ResolvedLlmConfig config = configResolver.resolve(null, null, null);
        ResolvedConfigOpenAiCompatibleLlmEmbeddingClient client =
                new ResolvedConfigOpenAiCompatibleLlmEmbeddingClient(config, apiKeyResolver, httpClient);
        var response =
                client.embed(
                        new LlmEmbeddingRequest(
                                config.embeddingModel(),
                                List.of("health-probe"),
                                timeoutMs,
                                Map.of()));
        return response != null && response.embeddings() != null && !response.embeddings().isEmpty();
    }
}
