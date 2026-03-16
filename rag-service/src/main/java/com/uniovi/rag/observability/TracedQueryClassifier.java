package com.uniovi.rag.observability;

import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.service.classifier.QueryClassifier;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link QueryClassifier}.
 * Wraps the delegate; when observability is present, classification calls are traced.
 */
public final class TracedQueryClassifier implements QueryClassifier {

    private static final int MAX_ATTR = 500;

    private final QueryClassifier delegate;
    private final ObservabilitySupport observability;

    public TracedQueryClassifier(QueryClassifier delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public QueryType classify(String query) {
        if (observability == null) {
            return delegate.classify(query);
        }
        observability.recordCounter("rag.classifier.calls", "operation", "classify");
        return observability.recordTimer("rag.classifier.classify", () ->
                observability.runWithSpan(
                        "rag.classifier.classify",
                        Map.of("query", truncate(query != null ? query : "")),
                        "queryType",
                        () -> delegate.classify(query)));
    }

    @Override
    public String classifyWithText(String query) {
        if (observability == null) {
            return delegate.classifyWithText(query);
        }
        observability.recordCounter("rag.classifier.calls", "operation", "classifyWithText");
        return observability.recordTimer("rag.classifier.classifyWithText", () ->
                observability.runWithSpan(
                        "rag.classifier.classifyWithText",
                        Map.of("query", truncate(query != null ? query : "")),
                        "queryType",
                        () -> delegate.classifyWithText(query)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
