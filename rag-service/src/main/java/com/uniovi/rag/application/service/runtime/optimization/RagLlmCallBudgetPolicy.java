package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.application.service.runtime.advisor.AnswerQualityAdvisor;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Per-preset LLM call budgets and conditional skip rules for secondary runtime calls.
 */
public final class RagLlmCallBudgetPolicy {

    public record SkipDecision(boolean skip, String reason) {
        public static SkipDecision run(String reason) {
            return new SkipDecision(false, reason);
        }

        public static SkipDecision skip(String reason) {
            return new SkipDecision(true, reason);
        }
    }

    public record PresetBudget(int maxSecondaryCalls, int maxTotalCalls, boolean interactive) {}

    private RagLlmCallBudgetPolicy() {}

    public static PresetBudget budgetFor(ExecutionContext ctx) {
        RagConfig rag = ctx.resolved().toRagConfig();
        if (!rag.memoryEnabled() && !rag.judgeEnabled() && !rag.reasoningEnabled()) {
            return new PresetBudget(1, 2, true);
        }
        if (rag.memoryEnabled() && rag.judgeEnabled()) {
            return new PresetBudget(3, 4, false);
        }
        if (rag.reasoningEnabled() || rag.judgeEnabled()) {
            return new PresetBudget(2, 3, true);
        }
        return new PresetBudget(2, 3, true);
    }

    public static boolean isInteractiveChat(ExecutionContext ctx) {
        return ctx.operationKind() == RuntimeOperationKind.CHAT_MESSAGE;
    }

    public static SkipDecision condenseDecision(
            ExecutionContext ctx, List<ConversationMemoryTurn> eligible, String latestUserTurn) {
        if (eligible == null || eligible.size() < 2) {
            return SkipDecision.skip("single_turn_or_no_history");
        }
        if (ConversationCondensePolicy.isSelfContainedQuestion(latestUserTurn)) {
            return SkipDecision.skip("self_contained_question");
        }
        if (!ConversationCondensePolicy.requiresMemoryReference(latestUserTurn)) {
            return SkipDecision.skip("no_pronoun_or_follow_up_reference");
        }
        return SkipDecision.run("multiturn_reference_detected");
    }

    public static SkipDecision llmRewriteDecision(ExecutionContext ctx, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return SkipDecision.skip("blank_query");
        }
        if (DeterministicQueryRewriteShortcuts.matches(normalizedQuery).isPresent()) {
            return SkipDecision.skip("deterministic_rewrite_available");
        }
        if (isClearFactualQuery(normalizedQuery)) {
            return SkipDecision.skip("clear_factual_query");
        }
        if (!ctx.resolved().toRagConfig().toolsEnabled()) {
            return SkipDecision.skip("tools_disabled");
        }
        return SkipDecision.run("rewrite_needed");
    }

    public static SkipDecision structuredPlanDecision(ExecutionContext ctx, QueryPlan plan) {
        if (!ctx.resolved().toRagConfig().reasoningEnabled()) {
            return SkipDecision.skip("reasoning_disabled");
        }
        if (plan == null) {
            return SkipDecision.skip("no_plan");
        }
        if (DeterministicStructuredAnswerPlanFactory.supports(plan)) {
            return SkipDecision.skip("deterministic_plan_sufficient");
        }
        if (isSimpleFactualPlan(plan)) {
            return SkipDecision.skip("simple_factual_question");
        }
        return SkipDecision.run("complex_question");
    }

    public static boolean allowJudgeRetry(
            ExecutionContext ctx,
            AnswerQualityAdvisor.AnswerQualityAssessment quality,
            boolean hasGroundedSources) {
        if (!isInteractiveChat(ctx)) {
            return true;
        }
        if (quality == null || quality.acceptable()) {
            return false;
        }
        if (quality.falseAbstention() || quality.unsupportedPositive() || quality.wrongDate()) {
            return true;
        }
        if (hasGroundedSources && !quality.reasons().isEmpty()) {
            return quality.reasons().stream().anyMatch(r -> r.contains("unsupported") || r.contains("format"));
        }
        return false;
    }

    public static boolean skipLlmJudgeForInteractive(
            ExecutionContext ctx, AnswerQualityAdvisor.AnswerQualityAssessment quality) {
        return isInteractiveChat(ctx) && quality != null && quality.acceptable();
    }

    /**
     * High-risk interactive cases that still warrant an LLM judge when budget allows.
     */
    public static boolean requiresInteractiveLlmJudge(
            ExecutionContext ctx,
            AnswerQualityAdvisor.AnswerQualityAssessment quality,
            boolean hasGroundedSources) {
        if (!isInteractiveChat(ctx) || quality == null || quality.acceptable()) {
            return false;
        }
        if (quality.falseAbstention()) {
            return true;
        }
        if (quality.unsupportedPositive()) {
            return true;
        }
        if (quality.wrongDate()) {
            return true;
        }
        if (!hasGroundedSources && !quality.preserveWithoutLlmJudge()) {
            return true;
        }
        return quality.reasons().stream()
                .anyMatch(
                        r ->
                                r.contains("unsupported")
                                        || r.contains("format")
                                        || r.contains("external")
                                        || r.contains("leak"));
    }

    private static boolean isSimpleFactualPlan(QueryPlan plan) {
        Optional<QueryType> qt = plan.classifierQueryType();
        if (qt.isEmpty()) {
            return isClearFactualQuery(plan.normalizedQueryText());
        }
        return switch (qt.get()) {
            case GET_FIELD, FIND_PARAGRAPH, FILTER_AND_LIST, COUNT_DOCUMENTS, BOOLEAN_QUERY, GET_DURATION ->
                    true;
            default -> false;
        };
    }

    static boolean isClearFactualQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT);
        boolean actaCue =
                q.contains("acta")
                        || q.contains("reunión")
                        || q.contains("reunion")
                        || q.contains("asistent")
                        || q.contains("presidente")
                        || q.contains("lugares")
                        || q.contains("terrazas")
                        || q.contains("ascensor")
                        || q.contains("zonas comunes");
        boolean shapeCue =
                q.contains("en qué acta")
                        || q.contains("en que acta")
                        || q.contains("dime qué actas")
                        || q.contains("dime que actas")
                        || q.contains("a qué actas")
                        || q.contains("a que actas")
                        || q.contains("dime los lugares")
                        || q.contains("cuántas actas")
                        || q.contains("cuantas actas");
        return actaCue || shapeCue;
    }
}
