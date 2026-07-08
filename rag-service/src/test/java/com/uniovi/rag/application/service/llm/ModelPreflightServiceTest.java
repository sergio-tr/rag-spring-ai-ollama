package com.uniovi.rag.application.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.application.exception.llm.LlmRemoteFailures;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.evaluation.LabBenchmarkDefaultModelResolver;
import com.uniovi.rag.application.service.evaluation.StartBenchmarkRunRequest;
import com.uniovi.rag.application.service.evaluation.baseline.EvaluationModelAvailabilityGate;
import com.uniovi.rag.application.service.evaluation.judge.EvaluationJudgeLlmExecutor;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileResolver;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.health.ModelPreflightProperties;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.interfaces.rest.support.OllamaConnectivityChecker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ModelPreflightServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock private EvaluationModelAvailabilityGate availabilityGate;
    @Mock private EmbeddingSpaceGuard embeddingSpaceGuard;
    @Mock private ProjectIndexProfileResolver projectIndexProfileResolver;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private ResolvedLlmConfigResolver configResolver;
    @Mock private OllamaConnectivityChecker ollamaConnectivityChecker;
    @Mock private LlmManualHealthCheckService healthCheckService;
    @Mock private LabBenchmarkDefaultModelResolver defaultModelResolver;
    @Mock private EvaluationJudgeLlmExecutor evaluationJudgeLlmExecutor;

    private ModelPreflightService sut;

    @BeforeEach
    void setUp() {
        ModelPreflightProperties props = new ModelPreflightProperties();
        props.setProbeTimeoutMs(5_000);
        sut =
                new ModelPreflightService(
                        availabilityGate,
                        embeddingSpaceGuard,
                        projectIndexProfileResolver,
                        projectIndexProfileService,
                        configResolver,
                        ollamaConnectivityChecker,
                        healthCheckService,
                        props,
                        defaultModelResolver,
                        evaluationJudgeLlmExecutor);
    }

    @Test
    void requireProjectEmbeddingForIndexing_skipsStructuredSearch() {
        when(projectIndexProfileService.ensureDefault(PROJECT_ID))
                .thenReturn(profile(MaterializationStrategy.STRUCTURED_SEARCH, "ignored"));

        sut.requireProjectEmbeddingForIndexing(USER_ID, PROJECT_ID);

        verify(projectIndexProfileResolver, never()).resolveForIngestion(any(), any());
        verify(embeddingSpaceGuard, never()).assertFitsPhysicalVectorColumn(any());
    }

    @Test
    void requireProjectEmbeddingForIndexing_failsWhenEmbeddingUnavailable() {
        when(projectIndexProfileService.ensureDefault(PROJECT_ID))
                .thenReturn(profile(MaterializationStrategy.CHUNK_LEVEL, "bge-m3"));
        when(projectIndexProfileResolver.resolveForIngestion(eq(PROJECT_ID), any()))
                .thenReturn(
                        new ProjectIndexProfileResolver.ResolvedIngestionIndexProfile(
                                PROJECT_ID, "bge-m3", "bge-m3", LlmProvider.OPENAI_COMPATIBLE, Map.of()));
        when(availabilityGate.isEmbeddingModelAvailable(USER_ID, "bge-m3")).thenReturn(false);

        assertThatThrownBy(() -> sut.requireProjectEmbeddingForIndexing(USER_ID, PROJECT_ID))
                .isInstanceOf(LlmProviderException.class)
                .satisfies(ex -> assertThat(((LlmProviderException) ex).errorCode())
                        .isEqualTo(ErrorCode.EMBEDDING_MODEL_UNAVAILABLE));
    }

    @Test
    void requireProjectEmbeddingForIndexing_probesDimensionsWhenCatalogOk() {
        when(projectIndexProfileService.ensureDefault(PROJECT_ID))
                .thenReturn(profile(MaterializationStrategy.CHUNK_LEVEL, "bge-m3"));
        when(projectIndexProfileResolver.resolveForIngestion(eq(PROJECT_ID), any()))
                .thenReturn(
                        new ProjectIndexProfileResolver.ResolvedIngestionIndexProfile(
                                PROJECT_ID, "bge-m3", "bge-m3", LlmProvider.OPENAI_COMPATIBLE, Map.of()));
        when(availabilityGate.isEmbeddingModelAvailable(USER_ID, "bge-m3")).thenReturn(true);

        sut.requireProjectEmbeddingForIndexing(USER_ID, PROJECT_ID);

        verify(embeddingSpaceGuard).assertFitsPhysicalVectorColumn("bge-m3");
    }

    @Test
    void requireProjectEmbeddingForIndexing_dimensionMismatchMapsToStructuredCode() {
        when(projectIndexProfileService.ensureDefault(PROJECT_ID))
                .thenReturn(profile(MaterializationStrategy.CHUNK_LEVEL, "bge-m3"));
        when(projectIndexProfileResolver.resolveForIngestion(eq(PROJECT_ID), any()))
                .thenReturn(
                        new ProjectIndexProfileResolver.ResolvedIngestionIndexProfile(
                                PROJECT_ID, "bge-m3", "bge-m3", LlmProvider.OPENAI_COMPATIBLE, Map.of()));
        when(availabilityGate.isEmbeddingModelAvailable(USER_ID, "bge-m3")).thenReturn(true);
        doThrow(
                        new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "EMBEDDING_DIMENSION_MISMATCH: model outputs 1024 dimensions"))
                .when(embeddingSpaceGuard)
                .assertFitsPhysicalVectorColumn("bge-m3");

        assertThatThrownBy(() -> sut.requireProjectEmbeddingForIndexing(USER_ID, PROJECT_ID))
                .isInstanceOf(LlmProviderException.class)
                .satisfies(ex -> assertThat(((LlmProviderException) ex).errorCode())
                        .isEqualTo(ErrorCode.MODEL_DIMENSION_MISMATCH));
    }

    @Test
    void requireChatForMessage_failsWhenChatModelUnavailable() {
        when(configResolver.resolveForOrchestratedExecute(eq(USER_ID), eq(PROJECT_ID), eq(null), eq(null), any()))
                .thenAnswer(
                        inv -> {
                            Optional<String> override = inv.getArgument(4);
                            String model = override.orElse("gpt-4o");
                            return chatConfig(model, LlmProvider.OPENAI_COMPATIBLE);
                        });
        when(availabilityGate.isChatModelAvailable(USER_ID, "gpt-4o")).thenReturn(false);

        assertThatThrownBy(() -> sut.requireChatForMessage(USER_ID, PROJECT_ID, "gpt-4o"))
                .isInstanceOf(LlmProviderException.class)
                .satisfies(ex -> assertThat(((LlmProviderException) ex).errorCode())
                        .isEqualTo(ErrorCode.CHAT_MODEL_UNAVAILABLE));
    }

    @Test
    void requireModelsForBenchmark_llmJudgeRequiresJudgeModel() {
        when(availabilityGate.isChatModelAvailable(USER_ID, "candidate")).thenReturn(true);
        when(evaluationJudgeLlmExecutor.resolveJudgeModelIdForPreflight(USER_ID)).thenReturn("judge-1");
        when(configResolver.resolveForOrchestratedExecute(eq(USER_ID), eq(null), eq(null), eq(null), any()))
                .thenAnswer(
                        inv -> {
                            Optional<String> override = inv.getArgument(4);
                            String model = override.orElse("unknown");
                            return chatConfig(model, LlmProvider.OPENAI_COMPATIBLE);
                        });
        when(availabilityGate.isChatModelAvailable(USER_ID, "judge-1")).thenReturn(false);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        null,
                        null,
                        null,
                        EvaluationRunKind.SCIENCE,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of(),
                        "candidate",
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null,
                        false,
                        false,
                        List.of(),
                        List.of(),
                        null,
                        false,
                        null);

        assertThatThrownBy(
                        () ->
                                sut.requireModelsForBenchmark(
                                        USER_ID,
                                        BenchmarkKind.LLM_JUDGE_QA,
                                        req,
                                        List.of("candidate"),
                                        List.of()))
                .isInstanceOf(LlmProviderException.class)
                .satisfies(ex -> assertThat(((LlmProviderException) ex).errorCode())
                        .isEqualTo(ErrorCode.JUDGE_MODEL_UNAVAILABLE));
    }

    @Test
    void requireModelsForBenchmark_embeddingFailsPreflight() {
        when(availabilityGate.isEmbeddingModelAvailable(USER_ID, "emb-x")).thenReturn(false);
        when(configResolver.resolve(USER_ID, null, null))
                .thenReturn(chatConfig("gpt", LlmProvider.OPENAI_COMPATIBLE));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        null,
                        null,
                        null,
                        EvaluationRunKind.SCIENCE,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of("emb-x"),
                        false,
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null,
                        false,
                        false,
                        List.of(),
                        List.of(),
                        null,
                        false,
                        null);

        assertThatThrownBy(
                        () ->
                                sut.requireModelsForBenchmark(
                                        USER_ID,
                                        BenchmarkKind.EMBEDDING_RETRIEVAL,
                                        req,
                                        List.of(),
                                        List.of("emb-x")))
                .isInstanceOf(LlmProviderException.class)
                .satisfies(ex -> assertThat(((LlmProviderException) ex).errorCode())
                        .isEqualTo(ErrorCode.EMBEDDING_MODEL_UNAVAILABLE));
    }

    @Test
    void sanitizedProviderErrorDoesNotLeakSecrets() {
        LlmProviderException ex =
                LlmRemoteFailures.unauthorized(
                        LlmProvider.OPENAI_COMPATIBLE,
                        ModelPreflightOperation.CHAT.wireName(),
                        "gpt-4o",
                        "http://litellm:4000",
                        401);

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.MODEL_AUTH_FAILED);
        assertThat(ex.publicMessage().toLowerCase()).doesNotContain("bearer");
        assertThat(ex.publicMessage()).doesNotContain("sk-");
    }

    private static ProjectIndexProfile profile(MaterializationStrategy strategy, String embeddingModel) {
        return new ProjectIndexProfile(
                PROJECT_ID,
                strategy,
                false,
                null,
                embeddingModel,
                4000,
                200,
                "hash",
                Instant.now(),
                Instant.now());
    }

    private static ResolvedLlmConfig chatConfig(String model, LlmProvider provider) {
        return ResolvedLlmConfig.uniform(
                provider,
                "http://localhost:4000",
                model,
                "embed-model",
                null,
                null,
                0.7,
                30_000,
                null,
                Map.of());
    }
}
