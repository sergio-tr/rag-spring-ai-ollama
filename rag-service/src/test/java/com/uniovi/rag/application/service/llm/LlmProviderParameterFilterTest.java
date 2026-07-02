package com.uniovi.rag.application.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmGenerationParameterId;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.LlmRoutingBackend;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmException;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmFailureKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmProviderParameterFilterTest {

    private LlmProviderParameterFilter filter;

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
        filter = new LlmProviderParameterFilter(new LlmModelCatalogService(properties));
    }

    @Test
    void qwen35_9b_dropsPresencePenaltyFromOllamaBackend() {
        Map<String, Object> filtered =
                filter.filterAdditionalParameters(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "qwen3.5:9b",
                        Map.of("presencePenalty", 0.0, "topP", 1.0));

        assertThat(filtered).containsEntry("topP", 1.0);
        assertThat(filtered).doesNotContainKey("presencePenalty");
    }

    @Test
    void qwen35_9b_dropsFrequencyPenaltyFromOllamaBackend() {
        Map<String, Object> filtered =
                filter.filterAdditionalParameters(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "qwen3.5:9b",
                        Map.of("frequency_penalty", 0.0, "seed", 42L));

        assertThat(filtered).containsEntry("seed", 42L);
        assertThat(filtered).doesNotContainKey("frequency_penalty");
    }

    @Test
    void qwen35_9b_injectsThinkFalseForThinkingModels() {
        Map<String, Object> filtered =
                filter.filterAdditionalParameters(
                        LlmProvider.OPENAI_COMPATIBLE, "qwen3.5:9b", Map.of("topP", 1.0));

        assertThat(filtered).containsEntry("think", Boolean.FALSE);
    }

    @Test
    void gemma4_12b_omitsThinkWhenUnsupported() {
        Map<String, Object> filtered =
                filter.filterAdditionalParameters(
                        LlmProvider.OPENAI_COMPATIBLE, "gemma4:12b", Map.of("topP", 1.0));

        assertThat(filtered).doesNotContainKey("think");
    }

    @Test
    void gpt4o_preservesPresencePenaltyForOpenAiCompatibleBackend() {
        Map<String, Object> filtered =
                filter.filterAdditionalParameters(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "gpt-4o",
                        Map.of("presencePenalty", 0.2, "frequencyPenalty", 0.1));

        assertThat(filtered)
                .containsEntry("presencePenalty", 0.2)
                .containsEntry("frequencyPenalty", 0.1);
        assertThat(filter.resolveBackend(LlmProvider.OPENAI_COMPATIBLE, "gpt-4o"))
                .isEqualTo(LlmRoutingBackend.OPENAI_COMPATIBLE_API);
    }

    @Test
    void ollamaBackend_dropsResponseFormatAndStop() {
        Map<String, Object> filtered =
                filter.filterAdditionalParameters(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "qwen3.5:9b",
                        Map.of(
                                "responseFormat",
                                Map.of("type", "json_object"),
                                "stop",
                                List.of("END")));

        assertThat(filtered).doesNotContainKeys("responseFormat", "stop");
    }

    @Test
    void filterChatRequest_preservesTemperatureAndSupportedAdditionalParameters() {
        LlmChatRequest request =
                LlmChatRequest.of(
                        "qwen3.5:9b",
                        "sys",
                        "user",
                        0.0,
                        5_000,
                        Map.of("topP", 1.0, "presencePenalty", 0.0, "maxTokens", 512));

        LlmChatRequest filtered = filter.filterChatRequest(request, LlmProvider.OPENAI_COMPATIBLE);

        assertThat(filtered.temperature()).isEqualTo(0.0);
        assertThat(filtered.additionalParameters())
                .containsEntry("topP", 1.0)
                .containsEntry("maxTokens", 512)
                .containsEntry("think", Boolean.FALSE)
                .doesNotContainKey("presencePenalty");
    }

    @Test
    void isUnsupportedParamsError_detectsLiteLlmMessage() {
        assertThat(
                        LlmProviderParameterFilter.isUnsupportedParamsError(
                                OpenAiCompatibleLlmException.unsupportedParams(
                                        "litellm.UnsupportedParamsError: ollama does not support parameters: ['presence_penalty']")))
                .isTrue();
        assertThat(
                        LlmProviderParameterFilter.isUnsupportedParamsError(
                                new RuntimeException(
                                        "OpenAI-compatible chat failed with HTTP 400: litellm.UnsupportedParamsError: ollama does not support parameters: ['presence_penalty']")))
                .isTrue();
        assertThat(
                        LlmProviderParameterFilter.isUnsupportedParamsError(
                                OpenAiCompatibleLlmException.httpError(500, "server error")))
                .isFalse();
    }

    @Test
    void unsupportedParamsFailureKind_isDistinctFromModelError() {
        assertThat(OpenAiCompatibleLlmException.unsupportedParams("presence_penalty").kind())
                .isEqualTo(OpenAiCompatibleLlmFailureKind.UNSUPPORTED_PARAMS);
    }

    @Test
    void ollamaNativeProvider_resolvesOllamaBackend() {
        assertThat(filter.resolveBackend(LlmProvider.OLLAMA_NATIVE, "gemma3:4b"))
                .isEqualTo(LlmRoutingBackend.OLLAMA);
        assertThat(filter.isSupported(LlmRoutingBackend.OLLAMA, LlmGenerationParameterId.PRESENCE_PENALTY, "gemma3:4b"))
                .isFalse();
    }
}
