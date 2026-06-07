package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.service.runtime.query.expand.QueryExpander;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link QueryExpander}.
 * Wraps the delegate; when observability is present, each {@link #expand(String)} is traced.
 */
public final class TracedQueryExpander implements QueryExpander {

    private static final int MAX_ATTR = 500;

    private final QueryExpander delegate;
    private final ObservabilitySupport observability;

    public TracedQueryExpander(QueryExpander delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public String expand(String query) {
        if (observability == null) {
            return delegate.expand(query);
        }
        observability.recordCounter("rag.expander.calls", "expander", delegate.getClass().getSimpleName());
        return observability.recordTimer("rag.expander.expand", () ->
                observability.runWithSpan(
                        // Domain convention: query expansion is part of the query pipeline
                        "rag.query.expand",
                        Map.of("query", truncate(query != null ? query : "")),
                        // Domain convention: attribute name starts with `rag.`
                        "rag.query.expanded",
                        () -> delegate.expand(query)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
