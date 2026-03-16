package com.uniovi.rag.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Central support for tracing and metrics. Use this to create spans with attributes
 * and to record counters/timers for observability (OTEL/Jaeger and Prometheus).
 */
@Component
@ConditionalOnBean(Tracer.class)
public class ObservabilitySupport {

    private static final int MAX_ATTRIBUTE_LENGTH = 2048;

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public ObservabilitySupport(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs the given callable inside a new span. Records input attributes, and on success
     * records the result under the given outputTagName; on failure records error and rethrows.
     */
    public <T> T runWithSpan(String spanName, Map<String, String> inputAttributes,
                             String outputTagName, Supplier<T> callable) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            if (inputAttributes != null) {
                inputAttributes.forEach((k, v) -> span.tag(sanitizeKey(k), truncate(v)));
            }
            T result = callable.get();
            if (outputTagName != null && result != null) {
                span.tag(sanitizeKey(outputTagName), truncate(result.toString()));
            }
            span.end();
            return result;
        } catch (Throwable t) {
            span.tag("error", "true");
            span.tag("error.type", t.getClass().getSimpleName());
            span.tag("error.message", truncate(t.getMessage() != null ? t.getMessage() : t.toString()));
            span.end();
            throw t;
        }
    }

    /**
     * Runs the given runnable inside a new span. Records input attributes and error if thrown.
     */
    public void runWithSpan(String spanName, Map<String, String> inputAttributes, Runnable runnable) {
        runWithSpan(spanName, inputAttributes, (String) null, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Records a counter increment with optional tags (pairs: key1, value1, key2, value2, ...).
     */
    public void recordCounter(String name, String... tags) {
        Counter.Builder builder = Counter.builder(name);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        builder.register(meterRegistry).increment();
    }

    /**
     * Records a timer for the given callable (duration in the default time unit).
     */
    public <T> T recordTimer(String timerName, Callable<T> callable) throws Exception {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = callable.call();
            sample.stop(Timer.builder(timerName).register(meterRegistry));
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder(timerName).tag("error", "true").register(meterRegistry));
            throw e;
        }
    }

    /**
     * Wraps a supplier with a timer and returns the result.
     */
    public <T> T recordTimer(String timerName, Supplier<T> supplier) {
        try {
            return recordTimer(timerName, supplier::get);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Tracer getTracer() {
        return tracer;
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    private static String sanitizeKey(String key) {
        if (key == null) return "null";
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ATTRIBUTE_LENGTH ? value : value.substring(0, MAX_ATTRIBUTE_LENGTH) + "...";
    }
}
