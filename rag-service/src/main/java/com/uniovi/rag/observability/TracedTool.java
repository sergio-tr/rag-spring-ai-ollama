package com.uniovi.rag.observability;

import com.uniovi.rag.tool.Tool;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link Tool}.
 * Each {@link #execute(ToolExecutionContext)} is wrapped in a span with counter and timer.
 */
public final class TracedTool implements Tool {

    private static final int MAX_ATTR = 500;

    private final Tool delegate;
    private final ObservabilitySupport observability;

    public TracedTool(Tool delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public ToolResult execute(ToolExecutionContext context) {
        if (observability == null) {
            return delegate.execute(context);
        }
        String queryType = context != null && context.queryType() != null ? context.queryType().name() : "null";
        String query = context != null ? context.query() : "";
        observability.recordCounter("rag.tool.calls", "tool", delegate.getClass().getSimpleName(), "queryType", queryType);
        return observability.recordTimer("rag.tool.execute", () ->
                observability.runWithSpan(
                        "rag.tool.execute",
                        Map.of(
                                "queryType", queryType,
                                "query", truncate(query != null ? query : "")
                        ),
                        "source",
                        () -> delegate.execute(context)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
