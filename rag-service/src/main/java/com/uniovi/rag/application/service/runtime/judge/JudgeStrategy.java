package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.application.service.runtime.DeterministicToolTerminalAnswerGuard;
import com.uniovi.rag.application.service.runtime.advisor.AnswerQualityAdvisor;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.judge.JudgeDecision;
import com.uniovi.rag.domain.runtime.judge.JudgeEvaluation;
import com.uniovi.rag.domain.runtime.judge.JudgeExecutionResult;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import org.springframework.stereotype.Service;

import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class JudgeStrategy {

    private static final String STAGE_JUDGE_FINALIZE = "judge_finalize";
    private static final String STAGE_ANSWER_QUALITY = "answer_quality_advisor";

    private final JudgePolicyResolver policyResolver;
    private final JudgeEvaluator evaluator;
    private final JudgeRetryExecutor retryExecutor;
    private final AnswerQualityAdvisor answerQualityAdvisor;

    public JudgeStrategy(
            JudgePolicyResolver policyResolver,
            JudgeEvaluator evaluator,
            JudgeRetryExecutor retryExecutor,
            AnswerQualityAdvisor answerQualityAdvisor
    ) {
        this.policyResolver = policyResolver;
        this.evaluator = evaluator;
        this.retryExecutor = retryExecutor;
        this.answerQualityAdvisor = answerQualityAdvisor;
    }

    public JudgeExecutionResult execute(
            ExecutionContext ctx,
            QueryPlan plan,
            AdaptiveRouteKind routeKind,
            String workflowName,
            JudgeCandidateSource candidateSource,
            String candidateAnswerText,
            Optional<DeterministicToolKind> toolKind
    ) {
        List<ExecutionStageTrace> stages = new ArrayList<>();

        JudgeDecision decision = policyResolver.resolve(ctx, plan, routeKind, workflowName, candidateSource);
        stages.add(new ExecutionStageTrace(
                "judge_policy",
                0L,
                ExecutionStageOutcome.SUCCESS,
                "eligible=" + decision.eligible() + " retryAllowed=" + decision.retryAllowed()));

        if (!decision.eligible()) {
            stages.add(new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=NOT_ATTEMPTED"));
            return new JudgeExecutionResult(
                    false,
                    JudgeOutcome.NOT_ATTEMPTED,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        if (DeterministicToolTerminalAnswerGuard.shouldPreserveDeterministicToolAnswer(
                plan, candidateSource, candidateAnswerText)) {
            stages.add(
                    new ExecutionStageTrace(
                            STAGE_JUDGE_FINALIZE,
                            0L,
                            ExecutionStageOutcome.SUCCESS,
                            "outcome=ACCEPTED_DETERMINISTIC_TOOL_PRESERVED"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.ACCEPTED,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        AnswerQualityAdvisor.AnswerQualityAssessment quality =
                answerQualityAdvisor.assess(
                        ctx,
                        plan,
                        candidateAnswerText,
                        candidateSource,
                        toolKind);
        stages.add(
                new ExecutionStageTrace(
                        STAGE_ANSWER_QUALITY,
                        0L,
                        ExecutionStageOutcome.SUCCESS,
                        "acceptable="
                                + quality.acceptable()
                                + " preserve="
                                + quality.preserveWithoutLlmJudge()
                                + " reasons="
                                + String.join(",", quality.reasons())));

        if (quality.preserveWithoutLlmJudge()) {
            stages.add(
                    new ExecutionStageTrace(
                            STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=ACCEPTED_TOOL_PRESERVED"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.ACCEPTED,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        if (!quality.acceptable()) {
            JudgeOutcome rejected =
                    decision.retryAllowed() ? JudgeOutcome.RETRY_REQUESTED : JudgeOutcome.REJECTED_NO_RETRY;
            if (rejected == JudgeOutcome.RETRY_REQUESTED) {
                JudgeRetryExecutor.RetryResult retry =
                        retryExecutor.retry(plan.rewrittenQueryText(), candidateAnswerText, String.join("; ", quality.reasons()));
                stages.addAll(retry.stageTraces());
                if (retry.success()) {
                    stages.add(
                            new ExecutionStageTrace(
                                    STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=RETRY_SUCCEEDED"));
                    return new JudgeExecutionResult(
                            true,
                            JudgeOutcome.RETRY_SUCCEEDED,
                            true,
                            true,
                            true,
                            retry.answerText(),
                            true,
                            List.copyOf(stages));
                }
                stages.add(
                        new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=RETRY_FAILED"));
                return new JudgeExecutionResult(
                        true,
                        JudgeOutcome.RETRY_FAILED,
                        true,
                        true,
                        false,
                        candidateAnswerText,
                        false,
                        List.copyOf(stages));
            }
            stages.add(
                    new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=REJECTED_NO_RETRY"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.REJECTED_NO_RETRY,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        String contextText = AnswerQualityAdvisor.resolveContextText(ctx);
        JudgeEvaluation eval =
                evaluator.evaluate(
                        plan.rewrittenQueryText(), candidateAnswerText, contextText, decision.retryAllowed());
        stages.addAll(eval.stageTraces());

        if (eval.outcome() == JudgeOutcome.FAILED_SAFE) {
            stages.add(new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=FAILED_SAFE"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.FAILED_SAFE,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        if (eval.outcome() == JudgeOutcome.ACCEPTED) {
            stages.add(new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=ACCEPTED"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.ACCEPTED,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        if (eval.outcome() == JudgeOutcome.REJECTED_NO_RETRY) {
            stages.add(new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=REJECTED_NO_RETRY"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.REJECTED_NO_RETRY,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        // RETRY_REQUESTED only.
        if (!decision.retryAllowed()) {
            stages.add(new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=REJECTED_NO_RETRY"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.REJECTED_NO_RETRY,
                    false,
                    false,
                    false,
                    candidateAnswerText,
                    false,
                    List.copyOf(stages));
        }

        JudgeRetryExecutor.RetryResult retry = retryExecutor.retry(plan.rewrittenQueryText(), candidateAnswerText, eval.feedback());
        stages.addAll(retry.stageTraces());
        if (retry.success()) {
            stages.add(new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=RETRY_SUCCEEDED"));
            return new JudgeExecutionResult(
                    true,
                    JudgeOutcome.RETRY_SUCCEEDED,
                    true,
                    true,
                    true,
                    retry.answerText(),
                    true,
                    List.copyOf(stages));
        }

        stages.add(new ExecutionStageTrace(STAGE_JUDGE_FINALIZE, 0L, ExecutionStageOutcome.SUCCESS, "outcome=RETRY_FAILED"));
        return new JudgeExecutionResult(
                true,
                JudgeOutcome.RETRY_FAILED,
                true,
                true,
                false,
                candidateAnswerText,
                false,
                List.copyOf(stages));
    }
}

