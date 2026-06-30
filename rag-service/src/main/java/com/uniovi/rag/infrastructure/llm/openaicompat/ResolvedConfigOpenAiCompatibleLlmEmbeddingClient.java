package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;

/**
 * OpenAI-compatible {@link LlmEmbeddingClient} bound to a single {@link ResolvedLlmConfig} snapshot.
 */
public class ResolvedConfigOpenAiCompatibleLlmEmbeddingClient implements LlmEmbeddingClient {

    private final ResolvedLlmConfig config;
    private final OpenAiCompatibleApiKeyResolver apiKeyResolver;
    private final OpenAiCompatibleEmbeddingsHttpClient httpClient;

    public ResolvedConfigOpenAiCompatibleLlmEmbeddingClient(
            ResolvedLlmConfig config,
            OpenAiCompatibleApiKeyResolver apiKeyResolver,
            OpenAiCompatibleEmbeddingsHttpClient httpClient) {
        this.config = config;
        this.apiKeyResolver = apiKeyResolver;
        this.httpClient = httpClient;
    }

    @Override
    public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("llmBaseUrl is blank");
        }
        String model =
                request.model() != null && !request.model().isBlank()
                        ? request.model()
                        : config.embeddingModel();
        String apiKey = apiKeyResolver.resolve(config.effectiveApiKeyEnv());
        long timeoutMs = resolveTimeoutMs(request);
        OpenAiEmbeddingRequest apiRequest =
                OpenAiCompatibleEmbeddingMapper.toApiRequest(
                        new LlmEmbeddingRequest(model, request.texts(), request.timeoutMs(), request.additionalParameters()));
        OpenAiEmbeddingResponse apiResponse = httpClient.post(config.baseUrl(), apiKey, apiRequest, timeoutMs);
        return OpenAiCompatibleEmbeddingMapper.toPortResponse(apiResponse, model);
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI_COMPATIBLE;
    }

    private long resolveTimeoutMs(LlmEmbeddingRequest request) {
        if (request.timeoutMs() != null && request.timeoutMs() > 0) {
            return request.timeoutMs();
        }
        if (config.timeoutMs() != null && config.timeoutMs() > 0) {
            return config.timeoutMs();
        }
        return 60_000L;
    }
}
