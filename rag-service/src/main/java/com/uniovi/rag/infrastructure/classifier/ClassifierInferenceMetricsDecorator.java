package com.uniovi.rag.infrastructure.classifier;

import com.uniovi.rag.domain.model.QueryType;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Records Micrometer counters around classifier inference (optional; no behavior change vs delegate).
 * Uses {@code rag_classifier_calls_total} with {@code status} only (success vs null_result) to keep
 * cardinality bounded - no raw model UUIDs.
 */
public final class ClassifierInferenceMetricsDecorator implements QueryClassifier {

    private static final String METRIC_CALLS = "rag_classifier_calls";

    private final QueryClassifier delegate;
    private final MeterRegistry meterRegistry;

    public ClassifierInferenceMetricsDecorator(QueryClassifier delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public QueryType classify(String query) {
        QueryType t = delegate.classify(query);
        recordCall(t != null);
        return t;
    }

    @Override
    public QueryType classify(String query, String modelId) {
        QueryType t = delegate.classify(query, modelId);
        recordCall(t != null);
        return t;
    }

    @Override
    public String classifyWithText(String query) {
        String raw = delegate.classifyWithText(query);
        recordCall(raw != null && !raw.isBlank());
        return raw;
    }

    @Override
    public String classifyWithText(String query, String modelId) {
        String raw = delegate.classifyWithText(query, modelId);
        recordCall(raw != null && !raw.isBlank());
        return raw;
    }

    @Override
    public ClassifierInferenceResponse classifyInference(String query, String modelId) {
        ClassifierInferenceResponse response = delegate.classifyInference(query, modelId);
        recordCall(response != null && response.queryType() != null && !response.queryType().isBlank());
        return response;
    }

    private void recordCall(boolean success) {
        String status = success ? "success" : "null_result";
        meterRegistry.counter(METRIC_CALLS, "status", status).increment();
    }
}
