package com.uniovi.rag.service.async.lab;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.AsyncTaskType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.service.async.AsyncTaskMutationService;
import com.uniovi.rag.service.evaluation.EvaluationCanonicalPersistenceService;
import com.uniovi.rag.service.evaluation.EvaluationService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvalLlmJobHandlerTest {

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private RagFeatureConfiguration featureConfiguration;

    @Mock
    private RagImplementationProperties implementationProperties;

    @Mock
    private EvaluationCanonicalPersistenceService canonicalPersistence;

    @Mock
    private AsyncTaskMutationService mutation;

    @Test
    void taskType_isEvalLlm() {
        EvalLlmJobHandler h =
                new EvalLlmJobHandler(
                        evaluationService, featureConfiguration, implementationProperties, canonicalPersistence);
        assertThat(h.taskType()).isEqualTo(AsyncTaskType.EVAL_LLM);
    }

    @Test
    void run_disablesRetrievalInCopiedConfig_andPersistsWhenRunIdPresent() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        stubFeatureFlags(true, false);
        Map<String, Object> eval = Map.of("score", 1);
        when(evaluationService.evaluateWithConfiguration(
                        ArgumentMatchers.any(RagFeatureConfiguration.class),
                        eq(implementationProperties)))
                .thenReturn(eval);
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        new EvalLlmJobHandler(
                        evaluationService, featureConfiguration, implementationProperties, canonicalPersistence)
                .run(task, mutation);

        ArgumentCaptor<RagFeatureConfiguration> cfg = ArgumentCaptor.forClass(RagFeatureConfiguration.class);
        verify(evaluationService).evaluateWithConfiguration(cfg.capture(), eq(implementationProperties));
        assertThat(cfg.getValue().isUseRetrieval()).isFalse();
        assertThat(cfg.getValue().isToolsEnabled()).isTrue();
        assertThat(cfg.getValue().isFunctionCallingEnabled()).isFalse();

        verify(canonicalPersistence)
                .persistLlmJudgeFromEvaluationMap(runId, eval, BenchmarkKind.LLM_JUDGE_QA);
        verify(mutation).markSucceeded(taskId, eval);
    }

    @Test
    void run_marksRunFailed_thenRethrows() {
        UUID taskId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        stubFeatureFlags(false, false);
        when(evaluationService.evaluateWithConfiguration(
                        ArgumentMatchers.any(RagFeatureConfiguration.class),
                        eq(implementationProperties)))
                .thenThrow(new RuntimeException("eval failed"));
        AsyncTaskEntity task = task(taskId, Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, runId.toString()));

        assertThatThrownBy(
                        () ->
                                new EvalLlmJobHandler(
                                                evaluationService,
                                                featureConfiguration,
                                                implementationProperties,
                                                canonicalPersistence)
                                        .run(task, mutation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("eval failed");

        verify(canonicalPersistence).markRunFailed(runId, "eval failed");
    }

    @Test
    void run_withoutRunId_doesNotCallMarkRunFailedOnError() {
        UUID taskId = UUID.randomUUID();
        stubFeatureFlags(false, true);
        when(evaluationService.evaluateWithConfiguration(
                        ArgumentMatchers.any(RagFeatureConfiguration.class),
                        eq(implementationProperties)))
                .thenThrow(new IllegalStateException("x"));
        AsyncTaskEntity task = task(taskId, Map.of());

        assertThatThrownBy(
                        () ->
                                new EvalLlmJobHandler(
                                                evaluationService,
                                                featureConfiguration,
                                                implementationProperties,
                                                canonicalPersistence)
                                        .run(task, mutation))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(canonicalPersistence);
    }

    private void stubFeatureFlags(boolean tools, boolean functionCalling) {
        when(featureConfiguration.isExpansionEnabled()).thenReturn(true);
        when(featureConfiguration.isNerEnabled()).thenReturn(false);
        when(featureConfiguration.isToolsEnabled()).thenReturn(tools);
        when(featureConfiguration.isMetadataEnabled()).thenReturn(true);
        when(featureConfiguration.isReasoningEnabled()).thenReturn(false);
        when(featureConfiguration.isRankerEnabled()).thenReturn(false);
        when(featureConfiguration.isPostRetrievalEnabled()).thenReturn(true);
        when(featureConfiguration.isFunctionCallingEnabled()).thenReturn(functionCalling);
        when(featureConfiguration.isUseRetrieval()).thenReturn(true);
        when(featureConfiguration.isUseAdvisor()).thenReturn(false);
    }

    private static AsyncTaskEntity task(UUID id, Map<String, Object> payload) {
        AsyncTaskEntity t = Mockito.mock(AsyncTaskEntity.class);
        when(t.getId()).thenReturn(id);
        when(t.getRequestPayload()).thenReturn(payload);
        return t;
    }
}
