package com.uniovi.rag.infrastructure.observability;

import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceMdcBridgeTest {

    private final SimpleTracer tracer = new SimpleTracer();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void applyCopiesCurrentSpanContextToMdc() {
        var span = tracer.nextSpan().name("test").start();
        try (var ws = tracer.withSpan(span)) {
            TraceMdcBridge.apply(tracer);
            assertThat(MDC.get(TraceMdcBridge.MDC_TRACE_ID)).isEqualTo(span.context().traceId());
            assertThat(MDC.get(TraceMdcBridge.MDC_SPAN_ID)).isEqualTo(span.context().spanId());
        } finally {
            span.end();
        }
    }

    @Test
    void clearRemovesTraceKeys() {
        MDC.put(TraceMdcBridge.MDC_TRACE_ID, "x");
        MDC.put(TraceMdcBridge.MDC_SPAN_ID, "y");
        TraceMdcBridge.clear();
        assertThat(MDC.get(TraceMdcBridge.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(TraceMdcBridge.MDC_SPAN_ID)).isNull();
    }

    @Test
    void currentCorrelationTraceIdFallsBackToMdcWhenNoTracer() {
        MDC.put(TraceMdcBridge.MDC_TRACE_ID, "from-mdc");
        assertThat(TraceMdcBridge.currentCorrelationTraceId(null)).isEqualTo("from-mdc");
    }
}
