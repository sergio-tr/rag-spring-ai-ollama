package com.uniovi.rag.infrastructure.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

/**
 * Keeps {@link MDC} aligned with the active Micrometer {@link Tracer} context so that
 * {@code %X{traceId:-}} / {@code %X{spanId:-}} in logging patterns match the current span
 * (including after async hand-off when combined with {@link ContextPropagatingFutures}).
 */
public final class TraceMdcBridge {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    private TraceMdcBridge() {}

    /**
     * Copies the current span's trace and span ids into MDC, if a current span exists.
     */
    public static void apply(Tracer tracer) {
        if (tracer == null) {
            return;
        }
        Span span = tracer.currentSpan();
        if (span == null || span.context() == null) {
            return;
        }
        String traceId = span.context().traceId();
        String spanId = span.context().spanId();
        if (traceId != null && !traceId.isEmpty() && isMeaningfulTraceId(traceId)) {
            MDC.put(MDC_TRACE_ID, traceId);
        }
        if (spanId != null && !spanId.isEmpty()) {
            MDC.put(MDC_SPAN_ID, spanId);
        }
    }

    /**
     * Removes trace correlation keys from MDC (only the keys this class sets).
     */
    public static void clear() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }

    /**
     * Resolves a trace id for persistence and {@link com.uniovi.rag.domain.runtime.RagExecutionContext}:
     * prefers the active Micrometer trace, then MDC, then {@code null} (caller may substitute a UUID).
     */
    public static String currentCorrelationTraceId(Tracer tracer) {
        if (tracer != null) {
            Span span = tracer.currentSpan();
            if (span != null && span.context() != null) {
                String tid = span.context().traceId();
                if (tid != null && !tid.isEmpty() && isMeaningfulTraceId(tid)) {
                    return tid;
                }
            }
        }
        return MDC.get(MDC_TRACE_ID);
    }

    private static boolean isMeaningfulTraceId(String traceId) {
        if (traceId.length() != 32) {
            return true;
        }
        for (int i = 0; i < traceId.length(); i++) {
            if (traceId.charAt(i) != '0') {
                return true;
            }
        }
        return false;
    }
}
