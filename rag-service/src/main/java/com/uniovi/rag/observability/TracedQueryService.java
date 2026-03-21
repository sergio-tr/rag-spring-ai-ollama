package com.uniovi.rag.observability;

import com.uniovi.rag.model.QueryResponse;
import com.uniovi.rag.service.query.QueryService;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link QueryService}.
 * Wraps the delegate; when observability is present, each {@link #generateResponse(String, String)} is traced.
 */
public final class TracedQueryService implements QueryService {

    private static final int MAX_ATTR = 2048;

    private final QueryService delegate;
    private final ObservabilitySupport observability;

    public TracedQueryService(QueryService delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public QueryResponse generateResponse(String question, String chatModel) {
        if (observability == null) {
            return delegate.generateResponse(question, chatModel);
        }
        observability.recordCounter("rag.query.calls", "operation", "generateResponse");
        Map<String, String> input = Map.of("query", truncate(question != null ? question : ""));
        return observability.recordTimer("rag.query.generate", () ->
                observability.runWithSpan("rag.query.generate", input, (String) null, () -> delegate.generateResponse(question, chatModel)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
