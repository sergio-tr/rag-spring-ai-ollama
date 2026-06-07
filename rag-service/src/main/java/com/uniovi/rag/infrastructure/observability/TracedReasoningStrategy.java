package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import com.uniovi.rag.application.service.runtime.reasoning.ReasoningStrategy;
import org.json.JSONObject;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link ReasoningStrategy}.
 * Wraps the delegate; when observability is present, runPreStep and runPostStep are traced.
 */
public final class TracedReasoningStrategy implements ReasoningStrategy {

    private static final int MAX_ATTR = 500;

    private final ReasoningStrategy delegate;
    private final ObservabilitySupport observability;

    public TracedReasoningStrategy(ReasoningStrategy delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public ReasoningPreOutput runPreStep(String query, QueryType classification, JSONObject ner, String expandedQuery) {
        if (observability == null) {
            return delegate.runPreStep(query, classification, ner, expandedQuery);
        }
        observability.recordCounter("rag.reasoning.calls", "operation", "runPreStep");
        return observability.runWithSpan(
                "rag.reasoning.runPreStep",
                Map.of(
                        "query", truncate(query != null ? query : ""),
                        "classification", classification != null ? classification.name() : "null"
                ),
                (String) null,
                () -> delegate.runPreStep(query, classification, ner, expandedQuery));
    }

    @Override
    public PostStepOutput runPostStep(String query, String context, String draftResponse) {
        if (observability == null) {
            return delegate.runPostStep(query, context, draftResponse);
        }
        observability.recordCounter("rag.reasoning.calls", "operation", "runPostStep");
        return observability.runWithSpan(
                "rag.reasoning.runPostStep",
                Map.of("query", truncate(query != null ? query : "")),
                (String) null,
                () -> delegate.runPostStep(query, context, draftResponse));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
