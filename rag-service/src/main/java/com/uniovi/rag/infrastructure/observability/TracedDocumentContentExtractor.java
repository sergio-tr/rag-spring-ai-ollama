package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.Cluster;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Decorator that adds tracing and metrics to any {@link DocumentContentExtractor}.
 * Each extraction operation is wrapped in a span and counter.
 */
public final class TracedDocumentContentExtractor implements DocumentContentExtractor {

    private static final int MAX_ATTR = 500;
    private static final String METRIC_KEY_OPERATION = "operation";

    private static final String METRIC_KEY_RESULT = "result";

    private static final String METRIC_EXTRACTION_CALLS = "rag.extraction.calls";

    private static final String ATTR_CONTENT_LENGTH = "contentLength";

    private final DocumentContentExtractor delegate;
    private final ObservabilitySupport observability;

    public TracedDocumentContentExtractor(DocumentContentExtractor delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public String extractDate(String content) {
        return traced("extractDate", Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                () -> delegate.extractDate(content));
    }

    @Override
    public String extractRelevantFragment(String content, String query) {
        return traced("extractRelevantFragment",
                Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0), "query", truncate(query)),
                () -> delegate.extractRelevantFragment(content, query));
    }

    @Override
    public String extractTime(String content, String type) {
        return traced("extractTime", Map.of("type", type != null ? type : ""),
                () -> delegate.extractTime(content, type));
    }

    @Override
    public int extractAttendeeCount(String content) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "extractAttendeeCount");
        return observability.runWithSpan("rag.extraction.extractAttendeeCount",
                Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                METRIC_KEY_RESULT, () -> delegate.extractAttendeeCount(content));
    }

    @Override
    public int calculateDuration(String content) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "calculateDuration");
        return observability.runWithSpan("rag.extraction.calculateDuration",
                Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                METRIC_KEY_RESULT, () -> delegate.calculateDuration(content));
    }

    @Override
    public String extractLiteralField(String field, String content) {
        return traced("extractLiteralField", Map.of("field", field != null ? field : ""),
                () -> delegate.extractLiteralField(field, content));
    }

    @Override
    public List<String> extractAttendees(String content) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "extractAttendees");
        return observability.runWithSpan("rag.extraction.extractAttendees",
                Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                (String) null, () -> delegate.extractAttendees(content));
    }

    @Override
    public String extractAgenda(String content) {
        return traced("extractAgenda", Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                () -> delegate.extractAgenda(content));
    }

    @Override
    public int countProposals(String content) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "countProposals");
        return observability.runWithSpan("rag.extraction.countProposals",
                Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                METRIC_KEY_RESULT, () -> delegate.countProposals(content));
    }

    @Override
    public int countAgendaItems(String content) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "countAgendaItems");
        return observability.runWithSpan("rag.extraction.countAgendaItems",
                Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                METRIC_KEY_RESULT, () -> delegate.countAgendaItems(content));
    }

    @Override
    public int countQuestions(String content) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "countQuestions");
        return observability.runWithSpan("rag.extraction.countQuestions",
                Map.of(ATTR_CONTENT_LENGTH, String.valueOf(content != null ? content.length() : 0)),
                METRIC_KEY_RESULT, () -> delegate.countQuestions(content));
    }

    @Override
    public boolean containsAnyKeyword(String text, String[] keywords) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "containsAnyKeyword");
        return observability.runWithSpan("rag.extraction.containsAnyKeyword",
                Map.of("textLength", String.valueOf(text != null ? text.length() : 0)),
                METRIC_KEY_RESULT, () -> delegate.containsAnyKeyword(text, keywords));
    }

    @Override
    public <T> List<Cluster<T>> clusterItems(List<T> items, Function<T, String> contentExtractor,
                                               Function<T, String> typeExtractor, double similarityThreshold) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, "clusterItems");
        return observability.runWithSpan("rag.extraction.clusterItems",
                Map.of("itemCount", String.valueOf(items != null ? items.size() : 0)),
                (String) null, () -> delegate.clusterItems(items, contentExtractor, typeExtractor, similarityThreshold));
    }

    private String traced(String operation, Map<String, String> attrs, Supplier<String> supplier) {
        observability.recordCounter(METRIC_EXTRACTION_CALLS, METRIC_KEY_OPERATION, operation);
        Map<String, String> full = new HashMap<>(attrs);
        full.put(METRIC_KEY_OPERATION, operation);
        return observability.runWithSpan("rag.extraction." + operation, full, METRIC_KEY_RESULT, supplier);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
