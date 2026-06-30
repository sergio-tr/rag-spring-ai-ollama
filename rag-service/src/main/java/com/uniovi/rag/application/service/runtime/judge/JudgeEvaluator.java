package com.uniovi.rag.application.service.runtime.judge;

import com.uniovi.rag.application.config.ConfigurablePromptResolver;
import com.uniovi.rag.application.config.ConfigurablePromptRuntimeSupport;
import com.uniovi.rag.domain.config.prompt.ConfigurablePromptGroup;
import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.application.service.runtime.RuntimePromptBudgeter;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.judge.JudgeEvaluation;
import com.uniovi.rag.domain.runtime.judge.JudgeOutcome;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class JudgeEvaluator {

    public static final String OPERATION_RUNTIME_JUDGE = "runtime-judge";

    private final ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;
    private final RuntimePromptBudgeter promptBudgeter;
    private final ConfigurablePromptResolver promptResolver;

    public JudgeEvaluator(
            ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor,
            RuntimePromptBudgeter promptBudgeter,
            ConfigurablePromptResolver promptResolver) {
        this.secondaryLlmExecutor = secondaryLlmExecutor;
        this.promptBudgeter = promptBudgeter;
        this.promptResolver = promptResolver;
    }

    public JudgeEvaluation evaluate(
            ExecutionContext ctx, String queryText, String candidateAnswerText, boolean retryAllowed) {
        return evaluate(ctx, queryText, candidateAnswerText, "", retryAllowed);
    }

    public JudgeEvaluation evaluate(
            ExecutionContext ctx,
            String queryText,
            String candidateAnswerText,
            String contextText,
            boolean retryAllowed) {
        Objects.requireNonNull(ctx, "ctx");
        long startNanos = System.nanoTime();
        try {
            RuntimePromptBudgeter.BudgetResult budget =
                    promptBudgeter != null
                            ? promptBudgeter.budgetForJudgeCandidateAnswer(candidateAnswerText)
                            : RuntimePromptBudgeter.truncate(
                                    "judge_candidate_answer", candidateAnswerText, 4_000, "default_judge_max_answer_chars");
            RuntimePromptBudgeter.BudgetResult contextBudget =
                    promptBudgeter != null
                            ? promptBudgeter.budgetForJudgeCandidateAnswer(contextText)
                            : RuntimePromptBudgeter.truncate(
                                    "judge_context", contextText, 6_000, "default_judge_max_context_chars");
            String prompt =
                    buildPrompt(
                            ctx, queryText, budget.textUsed(), contextBudget.textUsed(), retryAllowed);
            String raw =
                    secondaryLlmExecutor.complete(
                            ctx,
                            OPERATION_RUNTIME_JUDGE,
                            null,
                            prompt,
                            ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE);
            ResolvedLlmConfig config = secondaryLlmExecutor.effectiveConfig(ctx);
            JudgeOutcome o = parseOutcome(raw, retryAllowed);
            String feedback = extractFeedback(raw);
            return new JudgeEvaluation(
                    o,
                    Optional.empty(),
                    feedback,
                    List.of(
                            new ExecutionStageTrace(
                                    "judge_evaluate",
                                    millisSince(startNanos),
                                    ExecutionStageOutcome.SUCCESS,
                                    "outcome="
                                            + o
                                            + " truncated="
                                            + budget.truncated()
                                            + " contextChars="
                                            + contextBudget.originalChars()
                                            + " operation="
                                            + OPERATION_RUNTIME_JUDGE
                                            + " provider="
                                            + config.chatProvider()
                                            + " model="
                                            + config.chatModel())));
        } catch (Exception e) {
            return new JudgeEvaluation(
                    JudgeOutcome.FAILED_SAFE,
                    Optional.empty(),
                    "",
                    List.of(
                            new ExecutionStageTrace(
                                    "judge_evaluate",
                                    millisSince(startNanos),
                                    ExecutionStageOutcome.FAILED,
                                    "error=" + e.getClass().getSimpleName())));
        }
    }

    private String buildPrompt(
            ExecutionContext ctx,
            String queryText,
            String candidateAnswerText,
            String contextText,
            boolean retryAllowed) {
        String retryLine = ConfigurablePromptRuntimeSupport.retryPolicyLine(promptResolver, ctx, retryAllowed);
        String contextBlock =
                contextText == null || contextText.isBlank()
                        ? RuntimeJudgePromptSources.EMPTY_CONTEXT_PLACEHOLDER
                        : contextText;
        String template =
                promptResolver.resolve(ConfigurablePromptGroup.RUNTIME_JUDGE, ctx.userId(), ctx.projectId());
        return template.formatted(
                queryText == null ? "" : queryText,
                contextBlock,
                candidateAnswerText == null ? "" : candidateAnswerText,
                retryLine);
    }

    private static JudgeOutcome parseOutcome(String raw, boolean retryAllowed) {
        if (raw == null) {
            return JudgeOutcome.FAILED_SAFE;
        }
        String first = raw.strip();
        int nl = first.indexOf('\n');
        if (nl >= 0) {
            first = first.substring(0, nl).trim();
        }
        return switch (first) {
            case "ACCEPTED" -> JudgeOutcome.ACCEPTED;
            case "REJECTED_NO_RETRY" -> JudgeOutcome.REJECTED_NO_RETRY;
            case "RETRY_REQUESTED" -> retryAllowed ? JudgeOutcome.RETRY_REQUESTED : JudgeOutcome.REJECTED_NO_RETRY;
            default -> JudgeOutcome.FAILED_SAFE;
        };
    }

    private static String extractFeedback(String raw) {
        if (raw == null) {
            return "";
        }
        int idx = raw.indexOf("FEEDBACK:");
        if (idx < 0) {
            return "";
        }
        return raw.substring(idx + "FEEDBACK:".length()).trim();
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
