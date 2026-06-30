package com.uniovi.rag.application.service.evaluation.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkDefaultModelResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.product.ProductDemoModel;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationJudgeLlmExecutorTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private LlmClientResolver llmClientResolver;
    @Mock private LlmChatClient openAiChatClient;

    private EvaluationJudgeLlmExecutor executor;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        when(configResolver.resolve(any(), eq(null), eq(null))).thenReturn(openAiConfig(properties));
        LabBenchmarkDefaultModelResolver defaultModelResolver = new LabBenchmarkDefaultModelResolver(configResolver);
        executor =
                new EvaluationJudgeLlmExecutor(
                        llmClientResolver,
                        configResolver,
                        defaultModelResolver,
                        evaluationRunRepository,
                        "");
    }

    @Test
    void evaluationJudgeUsesConfiguredProviderAwareClient() throws Exception {
        when(llmClientResolver.resolveChatClient(any())).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any()))
                .thenReturn(LlmChatResponse.ofContent("Correctness: 5 - Justification: ok"));

        try (var ignored = EvaluationJudgeExecutionScope.open(USER_ID, null)) {
            String out = executor.completeJudgeUserPrompt("judge this answer");
            assertThat(out).contains("Correctness");
        }

        ArgumentCaptor<ResolvedLlmConfig> configCaptor = ArgumentCaptor.forClass(ResolvedLlmConfig.class);
        verify(llmClientResolver).resolveChatClient(configCaptor.capture());
        assertThat(configCaptor.getValue().chatProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(configCaptor.getValue().chatModel())
                .isEqualTo(properties.getOpenAiCompatible().getDefaultChatModel());
    }

    @Test
    void evaluationJudgeDoesNotUseSpringAiOllamaDefaultWhenOpenAiCompatible() throws Exception {
        when(llmClientResolver.resolveChatClient(any())).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any()))
                .thenReturn(LlmChatResponse.ofContent("Correctness: 4 - Justification: ok"));

        try (var ignored = EvaluationJudgeExecutionScope.open(USER_ID, null)) {
            executor.completeJudgeUserPrompt("prompt");
        }

        ArgumentCaptor<ResolvedLlmConfig> configCaptor = ArgumentCaptor.forClass(ResolvedLlmConfig.class);
        verify(llmClientResolver).resolveChatClient(configCaptor.capture());
        assertThat(configCaptor.getValue().chatProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
        assertThat(configCaptor.getValue().chatModel()).isNotEqualTo("gemma3:4b");
    }

    @Test
    void evaluationJudgeFailureIsMappedToStructuredError() throws Exception {
        when(llmClientResolver.resolveChatClient(any())).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any())).thenThrow(new RuntimeException("upstream timeout"));

        try (var ignored = EvaluationJudgeExecutionScope.open(USER_ID, null)) {
            assertThatThrownBy(() -> executor.completeJudgeUserPrompt("prompt"))
                    .isInstanceOf(EvaluationJudgeException.class)
                    .satisfies(
                            ex -> {
                                EvaluationJudgeException judgeEx = (EvaluationJudgeException) ex;
                                assertThat(judgeEx.errorCode())
                                        .isEqualTo(EvaluationJudgeException.ERROR_CODE_INVOCATION_FAILED);
                                assertThat(judgeEx.judgeProvider()).isEqualTo(LlmProvider.OPENAI_COMPATIBLE);
                                assertThat(judgeEx.judgeModel())
                                        .isEqualTo(properties.getOpenAiCompatible().getDefaultChatModel());
                            });
        }
    }

    @Test
    void evaluationModelSourceDoesNotUseProductDemoModelAsTruth() {
        LlmModelCatalogService catalog = LlmModelCatalogTestSupport.catalogFrom(properties);
        String resolved = executor.resolveJudgeModelId(USER_ID);

        assertThat(resolved).isEqualTo(properties.getOpenAiCompatible().getDefaultChatModel());
        assertThat(resolved).isNotEqualTo(ProductDemoModel.GEMMA3_4B.modelId());
        assertThat(catalog.find(LlmProvider.OPENAI_COMPATIBLE, ProductDemoModel.GEMMA3_4B.modelId(), LlmModelCapability.CHAT))
                .isEmpty();
    }

    private static ResolvedLlmConfig openAiConfig(LlmProperties properties) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                properties.getOpenAiCompatible().getDefaultBaseUrl(),
                properties.getOpenAiCompatible().getDefaultChatModel(),
                properties.getOpenAiCompatible().getDefaultEmbeddingModel(),
                properties.getOpenAiCompatible().getDefaultApiKeyEnv(),
                null,
                null,
                null,
                null,
                Map.of());
    }
}
