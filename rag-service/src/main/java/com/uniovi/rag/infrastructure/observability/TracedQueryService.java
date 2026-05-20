package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link QueryExecutionService}.
 */
public final class TracedQueryService implements QueryExecutionService {

    private static final int MAX_ATTR = 2048;

    private final QueryExecutionService delegate;
    private final ObservabilitySupport observability;

    public TracedQueryService(QueryExecutionService delegate, ObservabilitySupport observability) {
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
        return observability.recordTimer(
                "rag.query.generate",
                () -> observability.runWithSpan(
                        "rag.query.generate", input, (String) null, () -> delegate.generateResponse(question, chatModel)));
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
