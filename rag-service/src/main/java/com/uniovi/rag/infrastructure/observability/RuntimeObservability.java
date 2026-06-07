package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Domain spans for Chat RAG and Lab runtime (M8). No-op when {@link ObservabilitySupport} is absent.
 */
@Component
@ConditionalOnBean(ObservabilitySupport.class)
public class RuntimeObservability {

    static final String SPAN_CHAT_ACCEPTED = "rag.chat.accepted";
    static final String SPAN_QUERY_GENERATE = "rag.query.generate";
    static final String SPAN_CONFIG_RESOLVE = "rag.runtime.config.resolve";
    static final String SPAN_PROMPT_COMPOSE = "rag.runtime.prompt.compose";
    static final String SPAN_SOURCES = "rag.runtime.sources.attribute";
    static final String SPAN_CHAT_PERSIST = "rag.chat.persist";
    static final String SPAN_LAB_CORPUS_PREFLIGHT = "rag.lab.corpus.preflight";
    static final String SPAN_LAB_CONFIG_PREFLIGHT = "rag.lab.config.preflight";
    static final String SPAN_LAB_RUN_ACCEPTED = "rag.lab.run.accepted";
    static final String SPAN_LAB_BENCHMARK_ITEM = "rag.lab.benchmark.item";
    static final String SPAN_LAB_RESULT_PERSIST = "rag.lab.result.persist";

    static final String METRIC_CHAT_REQUESTS = "rag_chat_requests_total";
    static final String METRIC_CHAT_ERRORS = "rag_chat_errors_total";
    static final String METRIC_LAB_RUNS = "rag_lab_runs_total";
    static final String METRIC_LAB_ITEMS = "rag_lab_items_total";
    static final String METRIC_LAB_SKIPPED = "rag_lab_skipped_total";
    static final String METRIC_CLASSIFIER_INVALID = "rag_classifier_invalid_output_total";

    private final ObservabilitySupport observability;

    public RuntimeObservability(ObservabilitySupport observability) {
        this.observability = observability;
    }

    public void chatAccepted(UUID conversationId, UUID projectId, UUID taskId, UUID userId) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (conversationId != null) {
            attrs.put("conversationId", conversationId.toString());
        }
        if (projectId != null) {
            attrs.put("projectId", projectId.toString());
        }
        if (taskId != null) {
            attrs.put("rag.task_id", taskId.toString());
        }
        if (userId != null) {
            attrs.put("rag.user_id", userId.toString());
        }
        observability.runWithSpan(SPAN_CHAT_ACCEPTED, TelemetryRedaction.safeAttributes(attrs), () -> {});
        observability.recordCounter(METRIC_CHAT_REQUESTS, "status", "accepted");
    }

    public void chatFailed(String errorCode) {
        String code = errorCode != null && !errorCode.isBlank() ? errorCode : "unknown";
        observability.recordCounter(METRIC_CHAT_ERRORS, "error_code", code);
    }

    public <T> T chatGenerate(ExecutionContext ctx, Supplier<T> work) {
        Map<String, String> attrs = RuntimeObservabilityAttributes.fromExecutionContext(ctx);
        Map<String, String> input = new LinkedHashMap<>(attrs);
        input.put("method", "chat");
        return observability.recordTimer(
                "rag.query.generate",
                () ->
                        observability.runWithSpan(
                                SPAN_QUERY_GENERATE,
                                TelemetryRedaction.safeAttributes(input),
                                null,
                                work));
    }

    public <T> T configResolve(
            UUID userId, UUID projectId, String presetKey, int blockingIssueCount, Supplier<T> work) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (userId != null) {
            attrs.put("rag.user_id", userId.toString());
        }
        if (projectId != null) {
            attrs.put("projectId", projectId.toString());
        }
        if (presetKey != null && !presetKey.isBlank()) {
            attrs.put("presetKey", presetKey);
        }
        attrs.put("blockingIssueCount", String.valueOf(Math.max(0, blockingIssueCount)));
        return observability.runWithSpan(SPAN_CONFIG_RESOLVE, TelemetryRedaction.safeAttributes(attrs), null, work);
    }

    public <T> T promptCompose(String workflowFamily, int promptCharCount, Supplier<T> work) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (workflowFamily != null && !workflowFamily.isBlank()) {
            attrs.put("workflowFamily", workflowFamily);
        }
        attrs.put("promptCharCount", String.valueOf(Math.max(0, promptCharCount)));
        return observability.runWithSpan(SPAN_PROMPT_COMPOSE, TelemetryRedaction.safeAttributes(attrs), null, work);
    }

    public void sourcesAttribute(ExecutionTrace trace) {
        Map<String, String> attrs = RuntimeObservabilityAttributes.fromExecutionTrace(trace);
        observability.runWithSpan(SPAN_SOURCES, attrs, () -> {});
    }

    public void chatPersist(UUID assistantMessageId, int sourceCount, long durationMs) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (assistantMessageId != null) {
            attrs.put("assistantMessageId", assistantMessageId.toString());
        }
        attrs.put("sourceCount", String.valueOf(Math.max(0, sourceCount)));
        attrs.put("durationMs", String.valueOf(Math.max(0, durationMs)));
        observability.runWithSpan(SPAN_CHAT_PERSIST, TelemetryRedaction.safeAttributes(attrs), () -> {});
    }

    public void labCorpusPreflight(UUID corpusId, String primaryBlocker, boolean runnable, UUID snapshotId) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (corpusId != null) {
            attrs.put("corpusId", corpusId.toString());
        }
        if (snapshotId != null) {
            attrs.put("snapshotId", snapshotId.toString());
        }
        if (primaryBlocker != null && !primaryBlocker.isBlank()) {
            attrs.put("corpusReasonCode", primaryBlocker);
            attrs.put("primaryBlocker", primaryBlocker);
        }
        attrs.put("corpusRunnable", String.valueOf(runnable));
        observability.runWithSpan(SPAN_LAB_CORPUS_PREFLIGHT, TelemetryRedaction.safeAttributes(attrs), () -> {});
    }

    public void labConfigPreflight(String presetKey, String configReasonCode, String benchmarkKind) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (presetKey != null && !presetKey.isBlank()) {
            attrs.put("presetKey", presetKey);
        }
        if (configReasonCode != null && !configReasonCode.isBlank()) {
            attrs.put("configReasonCode", configReasonCode);
        }
        if (benchmarkKind != null && !benchmarkKind.isBlank()) {
            attrs.put("benchmarkKind", benchmarkKind);
        }
        observability.runWithSpan(SPAN_LAB_CONFIG_PREFLIGHT, TelemetryRedaction.safeAttributes(attrs), () -> {});
    }

    public void labRunAccepted(UUID runId, UUID taskId, String benchmarkKind, UUID snapshotId) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (runId != null) {
            attrs.put("runId", runId.toString());
            attrs.put("rag.evaluation_run_id", runId.toString());
        }
        if (taskId != null) {
            attrs.put("rag.task_id", taskId.toString());
        }
        if (benchmarkKind != null && !benchmarkKind.isBlank()) {
            attrs.put("benchmarkKind", benchmarkKind);
        }
        if (snapshotId != null) {
            attrs.put("snapshotId", snapshotId.toString());
        }
        observability.runWithSpan(SPAN_LAB_RUN_ACCEPTED, TelemetryRedaction.safeAttributes(attrs), () -> {});
        observability.recordCounter(METRIC_LAB_RUNS, "benchmark_kind", benchmarkKind != null ? benchmarkKind : "unknown", "status", "accepted");
    }

    public void labBenchmarkItem(int itemIndex, String itemStatus, String skipReason) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("itemIndex", String.valueOf(itemIndex));
        if (itemStatus != null && !itemStatus.isBlank()) {
            attrs.put("itemStatus", itemStatus);
        }
        if (skipReason != null && !skipReason.isBlank()) {
            attrs.put("skipReason", skipReason);
        }
        observability.runWithSpan(SPAN_LAB_BENCHMARK_ITEM, TelemetryRedaction.safeAttributes(attrs), () -> {});
        String status = itemStatus != null && !itemStatus.isBlank() ? itemStatus : "unknown";
        observability.recordCounter(METRIC_LAB_ITEMS, "item_status", status);
        if ("skipped".equalsIgnoreCase(status) && skipReason != null && !skipReason.isBlank()) {
            labItemSkipped(skipReason);
        }
    }

    public void labItemSkipped(String skipReason) {
        String reason = skipReason != null && !skipReason.isBlank() ? skipReason : "unknown";
        if (reason.length() > 64) {
            reason = reason.substring(0, 64);
        }
        observability.recordCounter(METRIC_LAB_SKIPPED, "skip_reason", reason);
    }

    public void labResultPersist(UUID runId, int itemCount, int skippedCount) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (runId != null) {
            attrs.put("runId", runId.toString());
        }
        attrs.put("itemCount", String.valueOf(Math.max(0, itemCount)));
        attrs.put("skippedCount", String.valueOf(Math.max(0, skippedCount)));
        observability.runWithSpan(SPAN_LAB_RESULT_PERSIST, TelemetryRedaction.safeAttributes(attrs), () -> {});
        observability.recordCounter(METRIC_LAB_RUNS, "benchmark_kind", "unknown", "status", "completed");
    }

    public void classifierInvalidOutput() {
        observability.recordCounter(METRIC_CLASSIFIER_INVALID);
    }

    public void enrichCurrentSpan(Map<String, String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        Tracer tracer = observability.getTracer();
        if (tracer == null) {
            return;
        }
        Span span = tracer.currentSpan();
        if (span == null) {
            return;
        }
        TelemetryRedaction.safeAttributes(tags).forEach(span::tag);
    }
}
