package com.uniovi.rag.observability;

import com.uniovi.rag.service.analyser.QueryAnalyser;
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
                        Map.of("query", truncate(query != null ? query : "")),
                        "result",
                        () -> delegate.analyse(query)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
