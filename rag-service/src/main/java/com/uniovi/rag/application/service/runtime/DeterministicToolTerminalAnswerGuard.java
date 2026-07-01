package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.optimization.DeterministicToolPromptBudgetPolicy;
import com.uniovi.rag.application.service.runtime.routing.TerminalGetFieldRoutingSupport;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateValidationResult;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.AnswerFinality;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.Set;

/**
 * R1 answer-path guard: when enabled, successful Tier-A deterministic tool answers terminate without
 * retrieval fallback, judge rewrite, or destructive synthesis.
 */
public final class DeterministicToolTerminalAnswerGuard {

    private static final Set<QueryType> TIER_A_QUERY_TYPES =
            Set.of(
                    QueryType.COUNT_DOCUMENTS,
                    QueryType.COUNT_AND_EXPLAIN,
                    QueryType.GET_FIELD,
                    QueryType.FILTER_AND_LIST,
                    QueryType.BOOLEAN_QUERY,
                    QueryType.GET_DURATION,
                    QueryType.FIND_PARAGRAPH);

    private static volatile Boolean acceptanceGuardTestOverride;

    private DeterministicToolTerminalAnswerGuard() {}

    public static void setAcceptanceGuardTestOverride(Boolean enabled) {
        acceptanceGuardTestOverride = enabled;
    }

    public static boolean acceptanceAnswerPathGuardEnabled() {
        if (acceptanceGuardTestOverride != null) {
            return acceptanceGuardTestOverride;
        }
        return Boolean.parseBoolean(
                System.getenv().getOrDefault("RAG_ACCEPTANCE_ANSWER_PATH_GUARD", "false"));
    }

    public static boolean shouldFinishTerminal(
            QueryPlan plan,
            DeterministicToolExecutionResult toolResult,
            RouteCandidateValidationResult validation) {
        if (!isSuccessfulTool(toolResult)) {
            return false;
        }
        if (DeterministicToolPromptBudgetPolicy.qualifiesForToolDirectAnswer(plan, toolResult.answerText())) {
            return true;
        }
        if (!acceptanceAnswerPathGuardEnabled()) {
            return TerminalGetFieldRoutingSupport.shouldTerminateWithoutWorkflowFallback(plan, toolResult);
        }
        return isTierAQueryType(plan) && validation != null && validation.safe();
    }

    public static boolean shouldMarkDeterministicToolFinal(
            QueryPlan plan, RouteCandidateValidationResult validation) {
        return acceptanceAnswerPathGuardEnabled()
                && validation != null
                && validation.safe()
                && isTierAQueryType(plan);
    }

    public static AnswerFinality finalityForTerminal(boolean deterministicToolFinal) {
        return deterministicToolFinal ? AnswerFinality.DETERMINISTIC_TOOL_FINAL : AnswerFinality.STANDARD;
    }

    public static boolean shouldPreserveDeterministicToolAnswer(
            QueryPlan plan, JudgeCandidateSource candidateSource, String candidateAnswerText) {
        if (candidateSource != JudgeCandidateSource.DETERMINISTIC_TOOL
                || !acceptanceAnswerPathGuardEnabled()) {
            return false;
        }
        return isTierAQueryType(plan)
                && candidateAnswerText != null
                && !candidateAnswerText.isBlank();
    }

    private static boolean isSuccessfulTool(DeterministicToolExecutionResult toolResult) {
        return toolResult != null
                && toolResult.outcome() == DeterministicToolOutcome.EXECUTED_SUCCESS
                && toolResult.success()
                && toolResult.answerText() != null
                && !toolResult.answerText().isBlank();
    }

    private static boolean isTierAQueryType(QueryPlan plan) {
        return plan != null
                && plan.classifierQueryType().filter(TIER_A_QUERY_TYPES::contains).isPresent();
    }
}
