package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.application.service.runtime.advisor.AnswerQualityAdvisor;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.judge.JudgeDecision;
import com.uniovi.rag.domain.runtime.judge.JudgeEvaluation;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeKind;
import com.uniovi.rag.domain.runtime.judge.JudgeMode;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.application.service.runtime.DeterministicToolTerminalAnswerGuard;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.AmbiguityAssessment;
import com.uniovi.rag.domain.runtime.query.ClassifierStatus;
import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryIntent;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.query.StructuredRewriteResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JudgeStrategyTest {

    @AfterEach
    void resetGuardOverride() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(null);
    }

    @Test
    void execute_notEligible_returnsNotAttempted_andNoRetry() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.DISABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, false, false, List.of(), List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "a",
                        Optional.empty());

        assertThat(out.judgeAttempted()).isFalse();
        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.NOT_ATTEMPTED);
        assertThat(out.retryAttempted()).isFalse();
        verifyNoInteractions(evaluator);
        verifyNoInteractions(retry);
        verifyNoInteractions(qualityAdvisor);
    }

    @Test
    void execute_accepted_keepsCandidate() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(qualityAdvisor.assess(any(), any(), anyString(), any(), any()))
                .thenReturn(AnswerQualityAdvisor.AnswerQualityAssessment.accepted(false));
        when(evaluator.evaluate(any(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.ACCEPTED, Optional.empty(), "", List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate",
                        Optional.empty());

        assertThat(out.judgeAttempted()).isTrue();
        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.ACCEPTED);
        assertThat(out.finalAnswerText()).isEqualTo("candidate");
        assertThat(out.finalAnswerFromRetry()).isFalse();
        verifyNoInteractions(retry);
    }

    @Test
    void execute_preservedToolAnswer_skipsLlmJudge() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(
                        new JudgeDecision(
                                JudgeMode.ENABLED,
                                JudgeKind.POST_ANSWER_JUDGE,
                                JudgeCandidateSource.DETERMINISTIC_TOOL,
                                true,
                                false,
                                List.of(),
                                List.of()));
        when(qualityAdvisor.assess(any(), any(), anyString(), any(), any()))
                .thenReturn(AnswerQualityAdvisor.AnswerQualityAssessment.accepted(true));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                        "deterministic-tool",
                        JudgeCandidateSource.DETERMINISTIC_TOOL,
                        "tool-answer",
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));

        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.ACCEPTED);
        assertThat(out.finalAnswerText()).isEqualTo("tool-answer");
        assertThat(out.finalAnswerFromRetry()).isFalse();
        verifyNoInteractions(evaluator);
        verifyNoInteractions(retry);
    }

    @Test
    void execute_retryRequested_success_updatesFinalAnswer() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(qualityAdvisor.assess(any(), any(), anyString(), any(), any()))
                .thenReturn(AnswerQualityAdvisor.AnswerQualityAssessment.accepted(false));
        when(evaluator.evaluate(any(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.RETRY_REQUESTED, Optional.empty(), "fix", List.of()));
        when(retry.retry(any(), anyString(), anyString(), anyString()))
                .thenReturn(new JudgeRetryExecutor.RetryResult(true, "repaired", List.of(new ExecutionStageTrace("judge_retry_execute", 0L, ExecutionStageOutcome.SUCCESS, ""))));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate",
                        Optional.empty());

        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.RETRY_SUCCEEDED);
        assertThat(out.finalAnswerText()).isEqualTo("repaired");
        assertThat(out.finalAnswerFromRetry()).isTrue();
        assertThat(out.retryAttempted()).isTrue();
    }

    @Test
    void execute_retryRequested_failure_keepsCandidate() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(qualityAdvisor.assess(any(), any(), anyString(), any(), any()))
                .thenReturn(AnswerQualityAdvisor.AnswerQualityAssessment.accepted(false));
        when(evaluator.evaluate(any(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.RETRY_REQUESTED, Optional.empty(), "fix", List.of()));
        when(retry.retry(any(), anyString(), anyString(), anyString()))
                .thenReturn(new JudgeRetryExecutor.RetryResult(false, "", List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate",
                        Optional.empty());

        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.RETRY_FAILED);
        assertThat(out.finalAnswerText()).isEqualTo("candidate");
        assertThat(out.finalAnswerFromRetry()).isFalse();
        assertThat(out.retryAttempted()).isTrue();
    }

    @Test
    void execute_failedSafe_keepsCandidate() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(qualityAdvisor.assess(any(), any(), anyString(), any(), any()))
                .thenReturn(AnswerQualityAdvisor.AnswerQualityAssessment.accepted(false));
        when(evaluator.evaluate(any(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.FAILED_SAFE, Optional.empty(), "", List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate",
                        Optional.empty());

        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.FAILED_SAFE);
        assertThat(out.finalAnswerText()).isEqualTo("candidate");
        verifyNoInteractions(retry);
    }

    @Test
    void execute_acceptanceGuardDeterministicTool_preservesAnswerWithoutRetry() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(
                        new JudgeDecision(
                                JudgeMode.ENABLED,
                                JudgeKind.POST_ANSWER_JUDGE,
                                JudgeCandidateSource.DETERMINISTIC_TOOL,
                                true,
                                true,
                                List.of(),
                                List.of()));
        String preserved = "No existen actas correspondientes al año 2028 en el corpus.";

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        countPlan(),
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                        "deterministic-tool",
                        JudgeCandidateSource.DETERMINISTIC_TOOL,
                        preserved,
                        Optional.of(DeterministicToolKind.COUNT_DOCUMENTS_TOOL));

        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.ACCEPTED);
        assertThat(out.finalAnswerText()).isEqualTo(preserved);
        assertThat(out.finalAnswerFromRetry()).isFalse();
        verifyNoInteractions(evaluator);
        verifyNoInteractions(retry);
        verifyNoInteractions(qualityAdvisor);
    }

    @Test
    void execute_acceptanceGuardGetDurationTool_preservesAnswerWithoutRetry() {
        DeterministicToolTerminalAnswerGuard.setAcceptanceGuardTestOverride(true);
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        AnswerQualityAdvisor qualityAdvisor = mock(AnswerQualityAdvisor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry, qualityAdvisor);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(
                        new JudgeDecision(
                                JudgeMode.ENABLED,
                                JudgeKind.POST_ANSWER_JUDGE,
                                JudgeCandidateSource.DETERMINISTIC_TOOL,
                                true,
                                true,
                                List.of(),
                                List.of()));
        String preserved =
                "La reunión del 25 de febrero de 2026 comenzó a las 19:00 y terminó a las 20:30 (1 hora y 30 minutos / 90 minutos).";

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        durationPlan(),
                        AdaptiveRouteKind.DETERMINISTIC_TOOL_ROUTE,
                        "deterministic-tool",
                        JudgeCandidateSource.DETERMINISTIC_TOOL,
                        preserved,
                        Optional.of(DeterministicToolKind.GET_DURATION_TOOL));

        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.ACCEPTED);
        assertThat(out.finalAnswerText()).isEqualTo(preserved);
        assertThat(out.finalAnswerText()).contains("90");
        verifyNoInteractions(evaluator);
        verifyNoInteractions(retry);
        verifyNoInteractions(qualityAdvisor);
    }

    private static QueryPlan countPlan() {
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                "Número de actas en 2028.",
                "Número de actas en 2028.",
                "Número de actas en 2028.",
                "Número de actas en 2028.",
                QueryType.COUNT_DOCUMENTS.name(),
                Optional.of(QueryType.COUNT_DOCUMENTS),
                ClassifierStatus.OK,
                QueryIntent.COUNT,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled("Número de actas en 2028.", ""),
                ExpectedAnswerShape.SCALAR_COUNT,
                AmbiguityAssessment.sufficient(),
                "corr",
                "cls",
                List.of());
    }

    private static QueryPlan durationPlan() {
        String query = "Duración de la reunión del 25 de febrero de 2026.";
        return new QueryPlan(
                QueryPlan.VERSION_P6_QU_CORE_V1,
                query,
                query,
                query,
                query,
                QueryType.GET_DURATION.name(),
                Optional.of(QueryType.GET_DURATION),
                ClassifierStatus.OK,
                QueryIntent.FIND,
                Map.of(),
                List.of(),
                List.of(),
                EntityExtractionResult.emptyWithNote(""),
                StructuredRewriteResult.identityDisabled(query, ""),
                ExpectedAnswerShape.PARAGRAPH,
                AmbiguityAssessment.sufficient(),
                "corr",
                "cls",
                List.of());
    }
}
