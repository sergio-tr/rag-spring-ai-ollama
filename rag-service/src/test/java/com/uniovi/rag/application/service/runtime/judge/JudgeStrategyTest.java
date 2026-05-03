package com.uniovi.rag.application.service.runtime.judge;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JudgeStrategyTest {

    @Test
    void execute_notEligible_returnsNotAttempted_andNoRetry() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.DISABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, false, false, List.of(), List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "a");

        assertThat(out.judgeAttempted()).isFalse();
        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.NOT_ATTEMPTED);
        assertThat(out.retryAttempted()).isFalse();
        verifyNoInteractions(evaluator);
        verifyNoInteractions(retry);
    }

    @Test
    void execute_accepted_keepsCandidate() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(evaluator.evaluate(anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.ACCEPTED, Optional.empty(), "", List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate");

        assertThat(out.judgeAttempted()).isTrue();
        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.ACCEPTED);
        assertThat(out.finalAnswerText()).isEqualTo("candidate");
        assertThat(out.finalAnswerFromRetry()).isFalse();
        verifyNoInteractions(retry);
    }

    @Test
    void execute_retryRequested_success_updatesFinalAnswer() {
        JudgePolicyResolver policy = mock(JudgePolicyResolver.class);
        JudgeEvaluator evaluator = mock(JudgeEvaluator.class);
        JudgeRetryExecutor retry = mock(JudgeRetryExecutor.class);
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(evaluator.evaluate(anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.RETRY_REQUESTED, Optional.empty(), "fix", List.of()));
        when(retry.retry(anyString(), anyString(), anyString()))
                .thenReturn(new JudgeRetryExecutor.RetryResult(true, "repaired", List.of(new ExecutionStageTrace("judge_retry_execute", 0L, ExecutionStageOutcome.SUCCESS, ""))));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate");

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
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(evaluator.evaluate(anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.RETRY_REQUESTED, Optional.empty(), "fix", List.of()));
        when(retry.retry(anyString(), anyString(), anyString()))
                .thenReturn(new JudgeRetryExecutor.RetryResult(false, "", List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate");

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
        JudgeStrategy strategy = new JudgeStrategy(policy, evaluator, retry);

        when(policy.resolve(any(), any(), any(), anyString(), any()))
                .thenReturn(new JudgeDecision(JudgeMode.ENABLED, JudgeKind.POST_ANSWER_JUDGE, JudgeCandidateSource.WORKFLOW, true, true, List.of(), List.of()));
        when(evaluator.evaluate(anyString(), anyString(), anyBoolean()))
                .thenReturn(new JudgeEvaluation(JudgeOutcome.FAILED_SAFE, Optional.empty(), "", List.of()));

        JudgeExecutionResult out =
                strategy.execute(
                        mock(ExecutionContext.class),
                        TestPlans.plan(),
                        AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                        "DirectLlmWorkflow",
                        JudgeCandidateSource.WORKFLOW,
                        "candidate");

        assertThat(out.judgeOutcome()).isEqualTo(JudgeOutcome.FAILED_SAFE);
        assertThat(out.finalAnswerText()).isEqualTo("candidate");
        verifyNoInteractions(retry);
    }
}

