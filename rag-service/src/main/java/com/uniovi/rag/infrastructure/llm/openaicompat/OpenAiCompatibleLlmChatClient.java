package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible / LiteLLM {@link LlmChatClient} using {@code POST /v1/chat/completions} with Bearer auth.
 * Separate from Ollama native clients; not wired into the RAG runtime yet.
 */
@Component
public class OpenAiCompatibleLlmChatClient implements LlmChatClient {

    public static final String HEALTH_PROBE_SYSTEM_PROMPT = "responde siempre en español";
    public static final String HEALTH_PROBE_USER_PROMPT = "Responde solo con OK";

    private final LlmProperties llmProperties;
    private final OpenAiCompatibleApiKeyResolver apiKeyResolver;
    private final OpenAiCompatibleChatCompletionsHttpClient httpClient;

    public OpenAiCompatibleLlmChatClient(
            LlmProperties llmProperties,
            OpenAiCompatibleApiKeyResolver apiKeyResolver,
            OpenAiCompatibleChatCompletionsHttpClient httpClient) {
        this.llmProperties = llmProperties;
        this.apiKeyResolver = apiKeyResolver;
        this.httpClient = httpClient;
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        LlmOpenAiCompatibleDefaults config = llmProperties.getOpenAiCompatible();
        validateBaseUrl(config);
        if (request.model() == null || request.model().isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("chat model is blank");
        }
        String apiKey = apiKeyResolver.resolve(config.getDefaultApiKeyEnv());
        long timeoutMs = resolveTimeoutMs(request, config);
        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);
        OpenAiChatCompletionResponse apiResponse =
                httpClient.post(config.getDefaultBaseUrl(), apiKey, apiRequest, timeoutMs);
        return OpenAiCompatibleChatMapper.toPortResponse(apiResponse, request.model());
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OPENAI_COMPATIBLE;
    }

    /**
     * Minimal inference probe via {@code /v1/chat/completions}. Does not call {@code /health} or {@code /v1/models}.
     *
     * @return {@code true} when the proxy returns a non-blank assistant message
     */
    public boolean healthCheckViaChatCompletion() {
        LlmOpenAiCompatibleDefaults config = llmProperties.getOpenAiCompatible();
        validateBaseUrl(config);
        validateDefaultChatModel(config);
        Double temperature = config.getDefaultTemperature();
        int timeoutMs = (int) Math.min(Integer.MAX_VALUE, config.getDefaultTimeoutMs());
        LlmChatRequest probe =
                LlmChatRequest.of(
                        config.getDefaultChatModel(),
                        HEALTH_PROBE_SYSTEM_PROMPT,
                        HEALTH_PROBE_USER_PROMPT,
                        temperature,
                        timeoutMs,
                        java.util.Map.of());
        LlmChatResponse response = chat(probe);
        return response.content() != null && !response.content().isBlank();
    }

    private static void validateBaseUrl(LlmOpenAiCompatibleDefaults config) {
        if (config.getDefaultBaseUrl() == null || config.getDefaultBaseUrl().isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("default-base-url is blank");
        }
    }

    private static void validateDefaultChatModel(LlmOpenAiCompatibleDefaults config) {
        if (config.getDefaultChatModel() == null || config.getDefaultChatModel().isBlank()) {
            throw OpenAiCompatibleLlmException.missingConfiguration("default-chat-model is blank");
        }
    }

    private static long resolveTimeoutMs(LlmChatRequest request, LlmOpenAiCompatibleDefaults config) {
        if (request.timeoutMs() != null && request.timeoutMs() > 0) {
            return request.timeoutMs();
        }
        return config.getDefaultTimeoutMs();
    }
}
