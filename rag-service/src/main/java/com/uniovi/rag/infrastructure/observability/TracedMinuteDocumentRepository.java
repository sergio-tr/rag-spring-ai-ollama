package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.AddResult;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.infrastructure.persistence.MinuteDocumentRepository;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link MinuteDocumentRepository}.
 * Each operation is wrapped in a span with counter and timer.
 */
public final class TracedMinuteDocumentRepository implements MinuteDocumentRepository {

    private static final int MAX_ATTR = 500;
    private static final String METRIC_KEY_OPERATION = "operation";

    private final MinuteDocumentRepository delegate;
    private final ObservabilitySupport observability;

    public TracedMinuteDocumentRepository(MinuteDocumentRepository delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public AddResult addMinute(Minute minute) {
        if (observability == null) {
            return delegate.addMinute(minute);
        }
        observability.recordCounter("rag.repository.calls", METRIC_KEY_OPERATION, "addMinute");
        String minuteId = minute != null && minute.id() != null ? minute.id() : "null";
        return observability.recordTimer("rag.repository.addMinute", () ->
                observability.runWithSpan(
                        "rag.repository.addMinute",
                        Map.of("minuteId", truncate(minuteId)),
                        "result",
                        () -> delegate.addMinute(minute)));
    }

    @Override
    public int deleteById(String id) {
        if (observability == null) {
            return delegate.deleteById(id);
        }
        observability.recordCounter("rag.repository.calls", METRIC_KEY_OPERATION, "deleteById");
        return observability.recordTimer("rag.repository.deleteById", () ->
                observability.runWithSpan(
                        "rag.repository.deleteById",
                        Map.of("id", truncate(id != null ? id : "")),
                        "deletedCount",
                        () -> delegate.deleteById(id)));
    }

    @Override
    public boolean hasDocumentWithId(String id) {
        if (observability == null) {
            return delegate.hasDocumentWithId(id);
        }
        observability.recordCounter("rag.repository.calls", METRIC_KEY_OPERATION, "hasDocumentWithId");
        return observability.runWithSpan(
                "rag.repository.hasDocumentWithId",
                Map.of("id", truncate(id != null ? id : "")),
                "result",
                () -> delegate.hasDocumentWithId(id));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
