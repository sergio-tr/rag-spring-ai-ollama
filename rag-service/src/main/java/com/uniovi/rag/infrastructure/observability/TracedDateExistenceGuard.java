package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.query.guard.DateExistenceGuard;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;

import java.util.Map;
import java.util.Optional;

/**
 * Decorator that adds tracing and metrics to any {@link DateExistenceGuard}.
 */
public final class TracedDateExistenceGuard implements DateExistenceGuard {

    private static final int MAX_ATTR = 500;

    private final DateExistenceGuard delegate;
    private final ObservabilitySupport observability;

    public TracedDateExistenceGuard(DateExistenceGuard delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public Optional<ToolResult> checkNoActaForDate(String query, QueryType queryType, JSONObject nerEntities) {
        observability.recordCounter("rag.guard.calls", "operation", "checkNoActaForDate");
        return observability.recordTimer("rag.guard.checkNoActaForDate", () ->
                observability.runWithSpan(
                        "rag.guard.checkNoActaForDate",
                        Map.of(
                                "queryLength", TelemetryRedaction.queryLength(query),
                                "queryType", queryType != null ? queryType.name() : "null"
                ),
                "present",
                () -> delegate.checkNoActaForDate(query, queryType, nerEntities)
        ));
    }

}
