package com.uniovi.rag.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ObservabilitySupport}.
 */
class ObservabilitySupportTest {

    private Tracer tracer;
    private MeterRegistry meterRegistry;
    private ObservabilitySupport support;

    @BeforeEach
    void setUp() {
        tracer = new SimpleTracer();
        meterRegistry = new SimpleMeterRegistry();
        support = new ObservabilitySupport(tracer, meterRegistry);
    }

    @Test
    void runWithSpan_returnsResult() {
        String result = support.runWithSpan(
                "test.span",
                Map.of("key", "value"),
                "output",
                () -> "ok"
        );
        assertEquals("ok", result);
    }

    @Test
    void runWithSpan_nullOutputTagName_doesNotTagResult() {
        String result = support.runWithSpan(
                "test.span",
                Map.of("key", "value"),
                (String) null,
                () -> "ok"
        );
        assertEquals("ok", result);
    }

    @Test
    void runWithSpan_nullResult_doesNotFail() {
        String result = support.runWithSpan(
                "test.span",
                Map.of(),
                "out",
                () -> (String) null
        );
        assertNull(result);
    }

    @Test
    void runWithSpan_runnableVariant_runsWithoutReturn() {
        boolean[] ran = { false };
        support.runWithSpan("test.run", Map.of(), () -> ran[0] = true);
        assertTrue(ran[0]);
    }

    @Test
    void runWithSpan_throws_propagatesException() {
        assertThrows(RuntimeException.class, () ->
                support.runWithSpan("test.span", Map.of(), "out", () -> {
                    throw new RuntimeException("fail");
                })
        );
    }

    @Test
    void recordCounter_incrementsCounter() {
        support.recordCounter("my.counter");
        support.recordCounter("my.counter");
        double count = meterRegistry.find("my.counter").counter().count();
        assertEquals(2.0, count);
    }

    @Test
    void recordCounter_withTags_registersTags() {
        support.recordCounter("tagged.counter", "a", "1", "b", "2");
        double count = meterRegistry.find("tagged.counter").tag("a", "1").tag("b", "2").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void recordTimer_supplier_returnsValue() throws Exception {
        String out = support.recordTimer("my.timer", () -> "timed");
        assertEquals("timed", out);
        assertTrue(meterRegistry.find("my.timer").timer().count() >= 1);
    }

    @Test
    void recordTimer_callable_returnsValue() throws Exception {
        String out = support.recordTimer("my.timer", (() -> "callable"));
        assertEquals("callable", out);
    }

    @Test
    void recordTimer_throws_recordsErrorTagAndRethrows() {
        assertThrows(IllegalStateException.class, () ->
                support.recordTimer("err.timer", () -> {
                    throw new IllegalStateException("timer error");
                })
        );
        assertTrue(meterRegistry.find("err.timer").tag("error", "true").timer().count() >= 1);
    }

    @Test
    void getTracer_returnsInjectedTracer() {
        assertSame(tracer, support.getTracer());
    }

    @Test
    void getMeterRegistry_returnsInjectedRegistry() {
        assertSame(meterRegistry, support.getMeterRegistry());
    }
}
