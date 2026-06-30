package com.uniovi.rag.application.service.llm.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.lab.LabEvaluationModelDto;
import com.uniovi.rag.interfaces.rest.dto.lab.LabEvaluationModelsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabEvaluationModelsServiceTest {

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private EvaluationModelCatalogService evaluationModelCatalogService;

    private LabEvaluationModelsService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new LabEvaluationModelsService(configResolver, evaluationModelCatalogService);
        userId = UUID.randomUUID();
    }

    @Test
    void openAiCompatibleChat_doesNotDependOnOllamaTags() {
        when(configResolver.resolve(userId, null, null))
                .thenReturn(openAiConfig("gpt-oss:20b", "text-embedding-3-small"));
        when(evaluationModelCatalogService.listForUser(userId, LlmModelCapability.CHAT, false))
                .thenReturn(
                        new LlmCatalogResponseDto(
                                List.of(
                                        chatRow(
                                                LlmProvider.OPENAI_COMPATIBLE,
                                                "gpt-oss:20b",
                                                true,
                                                LlmCatalogRuntimeStatus.UNKNOWN,
                                                null),
                                        chatRow(
                                                LlmProvider.OLLAMA_NATIVE,
                                                "gemma3:4b",
                                                true,
                                                LlmCatalogRuntimeStatus.AVAILABLE,
                                                null))));

        LabEvaluationModelsResponseDto response = service.listForUser(userId, LlmModelCapability.CHAT, false);

        assertThat(response.effectiveProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(response.models()).extracting(LabEvaluationModelDto::modelName).containsExactly("gpt-oss:20b");
        assertThat(response.models().getFirst().evalSelectable()).isTrue();
    }

    @Test
    void openAiCompatibleUnavailable_sanitizesOllamaRuntimeDetail() {
        when(configResolver.resolve(userId, null, null)).thenReturn(openAiConfig("missing", "embed"));
        when(evaluationModelCatalogService.listForUser(userId, LlmModelCapability.CHAT, false))
                .thenReturn(
                        new LlmCatalogResponseDto(
                                List.of(
                                        chatRow(
                                                LlmProvider.OPENAI_COMPATIBLE,
                                                "missing",
                                                true,
                                                LlmCatalogRuntimeStatus.UNAVAILABLE,
                                                "Saved Ollama tag unavailable on server"))));

        LabEvaluationModelsResponseDto response = service.listForUser(userId, LlmModelCapability.CHAT, false);

        LabEvaluationModelDto row = response.models().getFirst();
        assertThat(row.evalSelectable()).isFalse();
        assertThat(row.blockedReason()).isEqualTo("Model not available on configured API");
        assertThat(row.blockedReason()).doesNotContainIgnoringCase("ollama");
        assertThat(row.blockedReason()).doesNotContain("tag");
    }

    @Test
    void openAiCompatibleNotInCatalog_usesProviderAwareReason() {
        when(configResolver.resolve(userId, null, null)).thenReturn(openAiConfig("gpt-oss:20b", "embed"));
        when(evaluationModelCatalogService.listForUser(userId, LlmModelCapability.CHAT, false))
                .thenReturn(
                        new LlmCatalogResponseDto(
                                List.of(
                                        chatRow(
                                                LlmProvider.OPENAI_COMPATIBLE,
                                                "orphan-model",
                                                false,
                                                LlmCatalogRuntimeStatus.UNKNOWN,
                                                null))));

        LabEvaluationModelsResponseDto response = service.listForUser(userId, LlmModelCapability.CHAT, false);

        assertThat(response.models().getFirst().blockedReason())
                .isEqualTo("Model not configured in OpenAI-compatible catalog");
    }

    @Test
    void ollamaNativeChat_preservesRuntimeValidation() {
        when(configResolver.resolve(userId, null, null))
                .thenReturn(ollamaConfig("gemma3:4b", "mxbai-embed-large"));
        when(evaluationModelCatalogService.listForUser(userId, LlmModelCapability.CHAT, false))
                .thenReturn(
                        new LlmCatalogResponseDto(
                                List.of(
                                        chatRow(
                                                LlmProvider.OLLAMA_NATIVE,
                                                "gemma3:4b",
                                                true,
                                                LlmCatalogRuntimeStatus.UNAVAILABLE,
                                                "Model not pulled in Ollama"))));
        when(evaluationModelCatalogService.hasCompatibleEmbeddingModels(userId)).thenReturn(true);

        LabEvaluationModelsResponseDto response = service.listForUser(userId, LlmModelCapability.CHAT, false);

        assertThat(response.effectiveProvider()).isEqualTo(LlmProvider.OLLAMA_NATIVE);
        assertThat(response.models().getFirst().blockedReason()).isEqualTo("Model not pulled in Ollama");
    }

    @Test
    void unknownRuntimeStatus_isEvalSelectableForOpenAiCompatible() {
        when(configResolver.resolve(userId, null, null)).thenReturn(openAiConfig("gpt-oss:20b", "embed"));
        when(evaluationModelCatalogService.listForUser(userId, LlmModelCapability.CHAT, false))
                .thenReturn(
                        new LlmCatalogResponseDto(
                                List.of(
                                        chatRow(
                                                LlmProvider.OPENAI_COMPATIBLE,
                                                "gpt-oss:20b",
                                                true,
                                                LlmCatalogRuntimeStatus.UNKNOWN,
                                                null))));

        LabEvaluationModelsResponseDto response = service.listForUser(userId, LlmModelCapability.CHAT, false);

        assertThat(response.models().getFirst().evalSelectable()).isTrue();
        assertThat(response.models().getFirst().runtimeStatus()).isEqualTo(LlmCatalogRuntimeStatus.UNKNOWN);
    }

    private static ResolvedLlmConfig openAiConfig(String chatModel, String embedModel) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                chatModel,
                embedModel,
                "KEY",
                null,
                null,
                null,
                null,
                Map.of());
    }

    private static ResolvedLlmConfig ollamaConfig(String chatModel, String embedModel) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OLLAMA_NATIVE,
                "http://localhost:11434",
                chatModel,
                embedModel,
                null,
                null,
                null,
                null,
                null,
                Map.of());
    }

    private static LlmCatalogModelDto chatRow(
            LlmProvider provider,
            String modelName,
            boolean available,
            LlmCatalogRuntimeStatus runtimeStatus,
            String runtimeDetail) {
        return new LlmCatalogModelDto(
                provider,
                modelName,
                LlmModelCapability.CHAT,
                available,
                true,
                true,
                runtimeStatus,
                runtimeDetail,
                null,
                null,
                LlmCatalogSource.PROPERTIES);
    }
}
