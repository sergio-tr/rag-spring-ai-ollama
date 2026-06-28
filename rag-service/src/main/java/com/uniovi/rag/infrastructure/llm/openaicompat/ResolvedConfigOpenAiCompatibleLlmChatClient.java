package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;

/**
 * OpenAI-compatible {@link LlmChatClient} bound to a single {@link ResolvedLlmConfig} snapshot (per user/request).
 */
public class ResolvedConfigOpenAiCompatibleLlmChatClient implements LlmChatClient {

    private final ResolvedLlmConfig config;
    private final OpenAiCompatibleApiKeyResolver apiKeyResolver;
    private final OpenAiCompatibleChatCompletionsHttpClient httpClient;

    public ResolvedConfigOpenAiCompatibleLlmChatClient(
            ResolvedLlmConfig config,
            OpenAiCompatibleApiKeyResolver apiKeyResolver,
            OpenAiCompatibleChatCompletionsHttpClient httpClient) {
        this.config = config;
        this.apiKeyResolver = apiKeyResolver;
        this.httpClient = httpClient;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("llmBaseUrl is blank");
        }
        String apiKey = apiKeyResolver.resolve(config.effectiveApiKeyEnv());
        long timeoutMs = resolveTimeoutMs(request);
        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);
        OpenAiChatCompletionResponse apiResponse =
                httpClient.post(config.baseUrl(), apiKey, apiRequest, timeoutMs);
        return OpenAiCompatibleChatMapper.toPortResponse(apiResponse, request.model());
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI_COMPATIBLE;
    }

    private long resolveTimeoutMs(LlmChatRequest request) {
        if (request.timeoutMs() != null && request.timeoutMs() > 0) {
            return request.timeoutMs();
        }
        if (config.timeoutMs() != null && config.timeoutMs() > 0) {
            return config.timeoutMs();
        }
        return 60_000L;
    }
}
