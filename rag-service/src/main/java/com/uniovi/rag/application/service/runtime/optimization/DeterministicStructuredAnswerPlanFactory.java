package com.uniovi.rag.application.service.runtime.optimization;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.reasoning.StructuredAnswerPlan;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Lightweight answer-plan hints without an LLM call. */
public final class DeterministicStructuredAnswerPlanFactory {

    private DeterministicStructuredAnswerPlanFactory() {}

    public static boolean supports(QueryPlan plan) {
        return build(plan).isPresent();
    }

    public static Optional<StructuredAnswerPlan> build(QueryPlan plan) {
        if (plan == null) {
            return Optional.empty();
        }
        String q = plan.rewrittenQueryText() != null ? plan.rewrittenQueryText() : plan.normalizedQueryText();
        if (q == null || q.isBlank()) {
            return Optional.empty();
        }
        String lower = q.toLowerCase(Locale.ROOT);

        if (lower.contains("en qué acta") || lower.contains("en que acta")) {
            return Optional.of(
                    new StructuredAnswerPlan(
                            "DETERMINISTIC_ACTA_LOOKUP",
                            "Identify the acta/document/date where the topic appears.",
                            List.of("acta filename", "meeting date", "topic mention in context"),
                            List.of(
                                    "Primary output must be acta identifier and/or meeting date.",
                                    "Do not replace with generic legal or thematic explanation.",
                                    "Use only retrieved context."),
                            List.of("Topic appears in context", "Acta name or date cited"),
                            "Acta lookup by topic"));
        }

        Optional<QueryType> qt = plan.classifierQueryType();
        if (qt.filter(t -> t == QueryType.FILTER_AND_LIST || t == QueryType.COUNT_DOCUMENTS).isPresent()) {
            return Optional.of(
                    new StructuredAnswerPlan(
                            "DETERMINISTIC_LIST",
                            "List all matching actas supported by retrieved evidence.",
                            List.of("all matching acta filenames or dates"),
                            List.of(
                                    "Enumerate every matching acta found in context.",
                                    "State limitation if evidence is partial."),
                            List.of("Each listed acta is supported by context"),
                            "List matching actas"));
        }

        if (lower.contains("a qué actas asiste") || lower.contains("a que actas asiste")) {
            return Optional.of(
                    new StructuredAnswerPlan(
                            "DETERMINISTIC_ATTENDANCE",
                            "List actas where the person appears as attendee.",
                            List.of("person name in attendee lists"),
                            List.of("List all actas with the person in context", "Cite sources"),
                            List.of("Person name present in context"),
                            "Person attendance lookup"));
        }

        return Optional.empty();
    }
}
