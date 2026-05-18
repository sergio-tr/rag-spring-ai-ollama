package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link QueryClassifier}.
 * Wraps the delegate; when observability is present, classification calls are traced.
 */
public final class TracedQueryClassifier implements QueryClassifier {

    private static final int MAX_ATTR = 500;
    private static final String METRIC_CALLS = "rag.classifier.calls";
    private static final String ATTR_OPERATION = "operation";
    private static final String ATTR_QUERY = "query";
    private static final String ATTR_MODEL_ID = "model_id";
    private static final String ATTR_MODE = "mode";
    private static final String OP_CLASSIFY = "classify";
    private static final String OP_CLASSIFY_WITH_TEXT = "classifyWithText";
    private static final String SPAN_QUERY_CLASSIFY = "rag.query.classify";
    private static final String OUTPUT_QUERY_TYPE = "rag.query.type";

    private final QueryClassifier delegate;
    private final ObservabilitySupport observability;

    public TracedQueryClassifier(QueryClassifier delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public QueryType classify(String query) {
        if (observability == null) {
            return delegate.classify(query);
        }
        observability.recordCounter(METRIC_CALLS, ATTR_OPERATION, OP_CLASSIFY);
        return observability.recordTimer("rag.classifier.classify", () ->
                observability.runWithSpan(
                        // Domain convention: classification is part of the query pipeline
                        SPAN_QUERY_CLASSIFY,
                        Map.of(ATTR_QUERY, truncate(query != null ? query : "")),
                        // Domain convention: attribute name starts with `rag.`
                        OUTPUT_QUERY_TYPE,
                        () -> delegate.classify(query)));
    }

    @Override
    public QueryType classify(String query, String modelId) {
        if (observability == null) {
            return delegate.classify(query, modelId);
        }
        observability.recordCounter(METRIC_CALLS, ATTR_OPERATION, OP_CLASSIFY);
        return observability.recordTimer("rag.classifier.classify", () ->
                observability.runWithSpan(
                        SPAN_QUERY_CLASSIFY,
                        Map.of(
                                ATTR_QUERY, truncate(query != null ? query : ""),
                                ATTR_MODEL_ID, truncate(modelId != null ? modelId : "")),
                        OUTPUT_QUERY_TYPE,
                        () -> delegate.classify(query, modelId)));
    }

    @Override
    public String classifyWithText(String query) {
        if (observability == null) {
            return delegate.classifyWithText(query);
        }
        observability.recordCounter(METRIC_CALLS, ATTR_OPERATION, OP_CLASSIFY_WITH_TEXT);
        return observability.recordTimer("rag.classifier.classifyWithText", () ->
                observability.runWithSpan(
                        SPAN_QUERY_CLASSIFY,
                        Map.of(
                                ATTR_QUERY, truncate(query != null ? query : ""),
                                ATTR_MODE, OP_CLASSIFY_WITH_TEXT
                        ),
                        OUTPUT_QUERY_TYPE,
                        () -> delegate.classifyWithText(query)));
    }

    @Override
    public String classifyWithText(String query, String modelId) {
        if (observability == null) {
            return delegate.classifyWithText(query, modelId);
        }
        observability.recordCounter(METRIC_CALLS, ATTR_OPERATION, OP_CLASSIFY_WITH_TEXT);
        return observability.recordTimer("rag.classifier.classifyWithText", () ->
                observability.runWithSpan(
                        SPAN_QUERY_CLASSIFY,
                        Map.of(
                                ATTR_QUERY, truncate(query != null ? query : ""),
                                ATTR_MODE, OP_CLASSIFY_WITH_TEXT,
                                ATTR_MODEL_ID, truncate(modelId != null ? modelId : "")
                        ),
                        OUTPUT_QUERY_TYPE,
                        () -> delegate.classifyWithText(query, modelId)));
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= MAX_ATTR ? s : s.substring(0, MAX_ATTR) + "...";
    }
}
