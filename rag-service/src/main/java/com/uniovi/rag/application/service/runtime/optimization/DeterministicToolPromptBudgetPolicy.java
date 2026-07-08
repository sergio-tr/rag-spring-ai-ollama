package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.application.service.runtime.RuntimePromptBudgeter;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolEvidenceHolder;
import com.uniovi.rag.application.service.runtime.advisor.MetadataToolContextAssembler;
import com.uniovi.rag.application.service.runtime.retrieval.AdvancedRetrievalPipeline;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.Optional;
import java.util.Set;

/** Caps primary-answer prompt size on deterministic metadata-tool hybrid paths. */
public final class DeterministicToolPromptBudgetPolicy {

    public static final int MAX_PRIMARY_ANSWER_INPUT_CHARS = 20_000;

    private static final Set<QueryType> TOOL_DIRECT_QUERY_TYPES =
            Set.of(
                    QueryType.COUNT_DOCUMENTS,
                    QueryType.FILTER_AND_LIST,
                    QueryType.COUNT_AND_EXPLAIN,
                    QueryType.GET_FIELD,
                    QueryType.COMPARE,
                    QueryType.BOOLEAN_QUERY);

    private DeterministicToolPromptBudgetPolicy() {}

    public static boolean shouldUseToolScopedContext(QueryPlan plan) {
        return shouldUseToolScopedContext(plan, null);
    }

    public static boolean shouldUseToolScopedContext(QueryPlan plan, String workflowName) {
        if (plan == null) {
            return false;
        }
        Optional<DeterministicToolEvidenceHolder.Evidence> evidence = DeterministicToolEvidenceHolder.get();
        if (evidence.isEmpty() || !evidence.get().highConfidence()) {
            return false;
        }
        Optional<QueryType> qt = plan.classifierQueryType();
        if (qt.filter(TOOL_DIRECT_QUERY_TYPES::contains).isPresent()) {
            return true;
        }
        String queryText = planQueryText(plan);
        if (MetadataToolContextAssembler.isHighConfidenceListOrCountQuery(queryText)) {
            return true;
        }
        if (isChunkDenseWorkflow(workflowName) && contextWithinPrimaryBudget(evidence.get())) {
            return true;
        }
        return false;
    }

    public static RuntimePromptBudgeter.BudgetResult budgetPrimaryAnswerContext(String contextText) {
        return RuntimePromptBudgeter.truncate(
                "primary-answer",
                contextText,
                MAX_PRIMARY_ANSWER_INPUT_CHARS,
                "deterministic_tool_path_max_chars");
    }

    public static boolean qualifiesForToolDirectAnswer(QueryPlan plan, String toolAnswerText) {
        return qualifiesForToolDirectAnswer(plan, toolAnswerText, null);
    }

    public static boolean qualifiesForToolDirectAnswer(
            QueryPlan plan, String toolAnswerText, String workflowName) {
        if (plan == null || toolAnswerText == null || toolAnswerText.isBlank()) {
            return false;
        }
        Optional<DeterministicToolEvidenceHolder.Evidence> evidence =
                DeterministicToolEvidenceHolder.get().filter(DeterministicToolEvidenceHolder.Evidence::highConfidence);
        if (evidence.isEmpty()) {
            return false;
        }
        Optional<QueryType> qt = plan.classifierQueryType();
        if (qt.filter(TOOL_DIRECT_QUERY_TYPES::contains).isPresent()) {
            return true;
        }
        if (isChunkDenseWorkflow(workflowName)
                && MetadataToolContextAssembler.isHighConfidenceListOrCountQuery(planQueryText(plan))
                && contextWithinPrimaryBudget(evidence.get())) {
            return true;
        }
        return false;
    }

    private static String planQueryText(QueryPlan plan) {
        String rewritten = plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText();
        if (!rewritten.isBlank()) {
            return rewritten;
        }
        return plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText();
    }

    private static boolean isChunkDenseWorkflow(String workflowName) {
        if (workflowName == null || workflowName.isBlank()) {
            return false;
        }
        return AdvancedRetrievalPipeline.WORKFLOW_CHUNK_DENSE_METADATA.equals(workflowName)
                || "ChunkDenseRagWorkflow".equals(workflowName);
    }

    private static boolean contextWithinPrimaryBudget(DeterministicToolEvidenceHolder.Evidence evidence) {
        String context = evidence.assembledContextText();
        if (context == null || context.isBlank()) {
            return false;
        }
        return budgetPrimaryAnswerContext(context).finalChars() <= MAX_PRIMARY_ANSWER_INPUT_CHARS;
    }
}
