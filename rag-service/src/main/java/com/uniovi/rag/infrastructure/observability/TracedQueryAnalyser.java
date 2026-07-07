package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.service.runtime.query.analyser.QueryAnalyser;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import org.json.JSONObject;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link QueryAnalyser}.
 * Wraps the delegate; when observability is present, each {@link #analyse(String)} is traced.
 */
public final class TracedQueryAnalyser implements QueryAnalyser {

    private static final int MAX_ATTR = 500;

    private final QueryAnalyser delegate;
    private final ObservabilitySupport observability;

    public TracedQueryAnalyser(QueryAnalyser delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public JSONObject analyse(String query) {
        if (observability == null) {
            return delegate.analyse(query);
        }
        observability.recordCounter("rag.analyser.calls", "analyser", delegate.getClass().getSimpleName());
        return observability.recordTimer("rag.analyser.analyse", () ->
                observability.runWithSpan(
                        "rag.analyser.analyse",
                        Map.of(
                                "queryLength", TelemetryRedaction.queryLength(query),
                                "hasEntities", "pending"),
                        "result",
                        () -> delegate.analyse(query)));
    }

    @Override
    public JSONObject analyse(ExecutionContext ctx, String query) {
        if (observability == null) {
            return delegate.analyse(ctx, query);
        }
        observability.recordCounter("rag.analyser.calls", "analyser", delegate.getClass().getSimpleName());
        return observability.recordTimer("rag.analyser.analyse", () ->
                observability.runWithSpan(
                        "rag.analyser.analyse",
                        Map.of(
                                "queryLength", TelemetryRedaction.queryLength(query),
                                "hasEntities", "pending"),
                        "result",
                        () -> delegate.analyse(ctx, query)));
    }

}
