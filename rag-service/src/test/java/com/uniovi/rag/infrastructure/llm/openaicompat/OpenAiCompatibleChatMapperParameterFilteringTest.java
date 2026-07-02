package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.service.llm.LlmProviderParameterFilter;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleChatMapperParameterFilteringTest {

    private LlmProviderParameterFilter parameterFilter;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        LlmOllamaDefaults ollama = properties.getOllama();
        ollama.setDefaultChatModel("qwen3.5:9b");
        ollama.setAvailableChatModels(List.of("qwen3.5:9b", "gemma4:12b"));
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("qwen3.5:9b");
        openAi.setAvailableChatModels(List.of("qwen3.5:9b", "gpt-4o"));
        parameterFilter = new LlmProviderParameterFilter(new LlmModelCatalogService(properties));
    }

    @Test
    void qwen35_9b_presencePenaltyZero_omittedFromApiPayload() {
        LlmChatRequest request =
                LlmChatRequest.of(
                        "qwen3.5:9b",
                        "sys",
                        "user",
                        0.0,
                        5_000,
                        Map.of("presencePenalty", 0.0, "topP", 1.0, "maxTokens", 512));

        LlmChatRequest filtered = parameterFilter.filterChatRequest(request, LlmProvider.OPENAI_COMPATIBLE);
        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(filtered);

        assertNull(apiRequest.presencePenalty());
        assertEquals(1.0, apiRequest.topP());
        assertEquals(512, apiRequest.maxTokens());
        assertEquals(Boolean.FALSE, apiRequest.think());
    }

    @Test
    void qwen35_9b_frequencyPenaltyZero_omittedFromApiPayload() {
        LlmChatRequest request =
                LlmChatRequest.of(
                        "qwen3.5:9b",
                        "sys",
                        "user",
                        0.0,
                        5_000,
                        Map.of("frequencyPenalty", 0.0));

        OpenAiChatCompletionRequest apiRequest =
                OpenAiCompatibleChatMapper.toApiRequest(
                        parameterFilter.filterChatRequest(request, LlmProvider.OPENAI_COMPATIBLE));

        assertNull(apiRequest.frequencyPenalty());
    }

    @Test
    void gpt4o_preservesPresencePenaltyInApiPayload() {
        LlmChatRequest request =
                LlmChatRequest.of(
                        "gpt-4o",
                        "sys",
                        "user",
                        0.2,
                        5_000,
                        Map.of("presencePenalty", 0.3, "frequencyPenalty", 0.1));

        OpenAiChatCompletionRequest apiRequest =
                OpenAiCompatibleChatMapper.toApiRequest(
                        parameterFilter.filterChatRequest(request, LlmProvider.OPENAI_COMPATIBLE));

        assertEquals(0.3, apiRequest.presencePenalty());
        assertEquals(0.1, apiRequest.frequencyPenalty());
        assertNull(apiRequest.think());
    }

    @Test
    void mapperWithoutFilter_doesNotDefaultThink() {
        LlmChatRequest request =
                LlmChatRequest.of("qwen3.5:2b", "sys", "Responde únicamente con la palabra OK.", 0.0, 5_000, Map.of());

        OpenAiChatCompletionRequest apiRequest = OpenAiCompatibleChatMapper.toApiRequest(request);

        assertNull(apiRequest.think());
    }

    @Test
    void mapHttpError_unsupportedParams400() {
        OpenAiCompatibleLlmException ex =
                OpenAiCompatibleChatMapper.mapHttpError(
                        400,
                        "litellm.UnsupportedParamsError: ollama does not support parameters: ['presence_penalty'], for model=qwen3.5:9b",
                        "http://x/v1/chat/completions");

        assertThat(ex.kind()).isEqualTo(OpenAiCompatibleLlmFailureKind.UNSUPPORTED_PARAMS);
    }
}
