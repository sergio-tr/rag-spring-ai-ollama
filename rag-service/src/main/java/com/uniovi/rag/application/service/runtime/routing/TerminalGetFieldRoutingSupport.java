package com.uniovi.rag.application.service.runtime.routing;

import com.uniovi.rag.application.service.runtime.query.QueryPlanSlotEnricher;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Terminal routing for dated metadata GET_FIELD queries — avoids workflow RAG overwriting deterministic answers.
 */
public final class TerminalGetFieldRoutingSupport {

    private static final Set<String> TERMINAL_METADATA_FIELDS =
            Set.of("attendees", "attendeescount", "numberofattendees", "role", "duration", "durationminutes");

    private TerminalGetFieldRoutingSupport() {}

    public static boolean shouldTerminateWithoutWorkflowFallback(
            QueryPlan plan, DeterministicToolExecutionResult toolResult) {
        if (!isTerminalMetadataGetField(plan)) {
            return false;
        }
        if (toolResult == null
                || toolResult.outcome() != DeterministicToolOutcome.EXECUTED_SUCCESS
                || !toolResult.success()) {
            return false;
        }
        String answer = toolResult.answerText();
        return answer != null && !answer.isBlank();
    }

    public static boolean isTerminalMetadataGetField(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        Optional<QueryType> queryType = plan.classifierQueryType();
        if (queryType.isEmpty() || queryType.get() != QueryType.GET_FIELD) {
            return false;
        }
        if (!hasExplicitDate(plan)) {
            return false;
        }
        return hasTerminalMetadataFieldSlot(plan);
    }

    public static boolean matchesTerminalToolKind(Optional<DeterministicToolKind> toolKind) {
        return toolKind.filter(k -> k == DeterministicToolKind.GET_FIELD_TOOL).isPresent();
    }

    private static boolean hasExplicitDate(QueryPlan plan) {
        if (plan.entityExtractionResult() != null && !plan.entityExtractionResult().dates().isEmpty()) {
            for (String iso : plan.entityExtractionResult().dates()) {
                if (iso != null && !iso.isBlank() && !iso.matches("\\d{4}-01-01")) {
                    return true;
                }
            }
        }
        String query =
                ((plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText())
                                + " "
                                + (plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText()))
                        .toLowerCase(Locale.ROOT);
        return query.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*")
                || query.matches(".*\\d{1,2}\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\\s+de\\s+\\d{4}.*");
    }

    private static boolean hasTerminalMetadataFieldSlot(QueryPlan plan) {
        String field = resolveFieldSlot(plan);
        if (field == null || field.isBlank()) {
            return false;
        }
        return TERMINAL_METADATA_FIELDS.contains(field.toLowerCase(Locale.ROOT));
    }

    private static String resolveFieldSlot(QueryPlan plan) {
        Map<String, String> slots = plan.slots();
        String field = slots != null ? slots.get("field") : null;
        if (field == null || field.isBlank()) {
            field =
                    plan.structuredRewriteResult()
                            .slotFilling()
                            .getOrDefault("field", "");
        }
        if (field == null || field.isBlank()) {
            field =
                    QueryPlanSlotEnricher.inferFieldSlot(plan.normalizedQueryText()).orElse("");
        }
        if (field == null || field.isBlank()) {
            String query =
                    ((plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText())
                                    + " "
                                    + (plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText()))
                            .toLowerCase(Locale.ROOT);
            if ((query.contains("cuantos") || query.contains("cuántos"))
                    && (query.contains("asistieron")
                            || query.contains("participante")
                            || query.contains("asistente"))) {
                return "attendeesCount";
            }
        }
        return field;
    }
}
