package com.uniovi.rag.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

import org.springframework.lang.Nullable;

/**
 * Central support for tracing and metrics. Use this to create spans with attributes
 * and to record counters/timers for observability (OTEL/Jaeger and Prometheus).
 */
@Component
@ConditionalOnBean(Tracer.class)
public class ObservabilitySupport {

    private static final int MAX_ATTRIBUTE_LENGTH = 2048;

    /** Micrometer / OpenTelemetry span tag marking an error span. */
    private static final String TAG_ERROR = "error";

    /** Timer and tracing span name for a single LLM invocation inside an execution workflow. */
    private static final String SPAN_RAG_AI_LLM_INVOKE = "rag.ai.llm.invoke";

    /** Tag key identifying which workflow issued an LLM call. */
    private static final String TAG_WORKFLOW = "workflow";

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
        } catch (Exception t) {
            span.tag(TAG_ERROR, "true");
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
     * Wraps a supplier with a timer and returns the result.
     */
    public <T> T recordTimer(String timerName, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = supplier.get();
            sample.stop(Timer.builder(timerName).register(meterRegistry));
            return result;
        } catch (RuntimeException e) {
            sample.stop(Timer.builder(timerName).tag(TAG_ERROR, "true").register(meterRegistry));
            throw e;
        }
    }

    /**
     * One LLM call from an {@link com.uniovi.rag.application.service.runtime.AbstractExecutionWorkflow} subclass:
     * Micrometer timer {@code rag.ai.llm.invoke} (tag {@code workflow}) plus tracing span {@code rag.ai.llm.invoke}.
     */
    public <T> T recordExecutionWorkflowLlmInvocation(@Nullable String workflowSimpleName, Supplier<T> supplier) {
        String wf = workflowSimpleName != null && !workflowSimpleName.isBlank() ? workflowSimpleName : "unknown";
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = runWithSpan(
                    SPAN_RAG_AI_LLM_INVOKE,
                    Map.of(TAG_WORKFLOW, wf),
                    null,
                    supplier);
            sample.stop(Timer.builder(SPAN_RAG_AI_LLM_INVOKE).tag(TAG_WORKFLOW, wf).register(meterRegistry));
            return result;
        } catch (RuntimeException e) {
            sample.stop(
                    Timer.builder(SPAN_RAG_AI_LLM_INVOKE)
                            .tag(TAG_WORKFLOW, wf)
                            .tag(TAG_ERROR, "true")
                            .register(meterRegistry));
            throw e;
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
