package com.uniovi.rag.application.service.evaluation.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.catalog.EvaluationModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogResponseDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationModelAvailabilityGateTest {

    @Mock private OllamaModelCatalogClient ollamaModelCatalogClient;
    @Mock private EvaluationModelCatalogService evaluationModelCatalogService;
    @Mock private ResolvedLlmConfigResolver configResolver;

    @Test
    void openAiCompatibleChat_usesCatalogNotOllamaProbe() {
        UUID userId = UUID.randomUUID();
        when(configResolver.resolve(userId, null, null))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "http://litellm:4000",
                                "gpt-oss:20b",
                                "text-embedding-3-small",
                                "KEY",
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
        when(evaluationModelCatalogService.listForUser(userId, LlmModelCapability.CHAT, false))
                .thenReturn(
                        new LlmCatalogResponseDto(
                                List.of(
                                        new LlmCatalogModelDto(
                                                LlmProvider.OPENAI_COMPATIBLE,
                                                "gpt-oss:20b",
                                                LlmModelCapability.CHAT,
                                                true,
                                                true,
                                                true,
                                                null,
                                                null,
                                                null,
                                                null,
                                                LlmCatalogSource.PROPERTIES))));

        EvaluationModelAvailabilityGate gate =
                new EvaluationModelAvailabilityGate(
                        ollamaModelCatalogClient, evaluationModelCatalogService, configResolver);

        assertThat(gate.isChatModelAvailable(userId, "gpt-oss:20b")).isTrue();
        verifyNoInteractions(ollamaModelCatalogClient);
    }

    @Test
    void openAiCompatibleChat_rejectsModelNotInCatalog() {
        UUID userId = UUID.randomUUID();
        when(configResolver.resolve(userId, null, null))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OPENAI_COMPATIBLE,
                                "http://litellm:4000",
                                "gpt-oss:20b",
                                "text-embedding-3-small",
                                "KEY",
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
        when(evaluationModelCatalogService.listForUser(userId, LlmModelCapability.CHAT, false))
                .thenReturn(new LlmCatalogResponseDto(List.of()));

        EvaluationModelAvailabilityGate gate =
                new EvaluationModelAvailabilityGate(
                        ollamaModelCatalogClient, evaluationModelCatalogService, configResolver);

        assertThat(gate.isChatModelAvailable(userId, "unknown-model")).isFalse();
        verifyNoInteractions(ollamaModelCatalogClient);
    }

    @Test
    void ollamaNativeChat_probesOllamaClient() {
        UUID userId = UUID.randomUUID();
        when(configResolver.resolve(userId, null, null))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OLLAMA_NATIVE,
                                "http://localhost:11434",
                                "gemma3:4b",
                                "mxbai-embed-large",
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
        when(ollamaModelCatalogClient.isModelAvailable("gemma3:4b")).thenReturn(true);

        EvaluationModelAvailabilityGate gate =
                new EvaluationModelAvailabilityGate(
                        ollamaModelCatalogClient, evaluationModelCatalogService, configResolver);

        assertThat(gate.isChatModelAvailable(userId, "gemma3:4b")).isTrue();
        verify(ollamaModelCatalogClient).isModelAvailable("gemma3:4b");
    }
}
