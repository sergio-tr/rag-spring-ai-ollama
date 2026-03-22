package com.uniovi.rag.observability;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.evaluation.EvaluationService;

import java.util.Map;

/**
 * Decorator that adds tracing and metrics to any {@link EvaluationService}.
 * Each operation is wrapped in a span and optional counter so evaluation
 * runs are visible in traces and metrics.
 */
public final class TracedEvaluationService implements EvaluationService {

    private final EvaluationService delegate;
    private final ObservabilitySupport observability;

    public TracedEvaluationService(EvaluationService delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public Map<String, Object> evaluate() {
        observability.recordCounter("rag.evaluation.calls", "operation", "evaluate");
        return observability.recordTimer("rag.evaluation.evaluate", () ->
                observability.runWithSpan(
                        // Domain convention: evaluation execution
                        "rag.evaluation.run",
                        Map.of(),
                        "result",
                        () -> delegate.evaluate()
                ));
    }

    @Override
    public Map<String, Object> evaluateWithConfiguration(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties) {
        observability.recordCounter("rag.evaluation.calls", "operation", "evaluateWithConfiguration");
        String configLabel = customConfig != null ? "custom" : "null";
        return observability.recordTimer("rag.evaluation.evaluateWithConfiguration", () ->
                observability.runWithSpan(
                        "rag.evaluation.run",
                        Map.of("rag.evaluation.id", configLabel),
                        "result",
                        () -> delegate.evaluateWithConfiguration(customConfig, implementationProperties)
                ));
    }

    @Override
    public Map<String, Map<String, Object>> evaluateAllConfigurations() {
        observability.recordCounter("rag.evaluation.calls", "operation", "evaluateAllConfigurations");
        return observability.recordTimer("rag.evaluation.evaluateAllConfigurations", () ->
                observability.runWithSpan(
                        "rag.evaluation.run",
                        Map.of("rag.evaluation.id", "all"),
                        "result",
                        () -> delegate.evaluateAllConfigurations()
                ));
    }

    @Override
    public void loadData() {
        observability.recordCounter("rag.evaluation.calls", "operation", "loadData");
        observability.recordTimer("rag.evaluation.loadData", () -> {
            observability.runWithSpan(
                    "rag.evaluation.run",
                    Map.of("operation", "loadData"),
                    () -> delegate.loadData()
            );
            return null;
        });
    }

    @Override
    public Map<String, String> getQuestionsAndAnswers() {
        observability.recordCounter("rag.evaluation.calls", "operation", "getQuestionsAndAnswers");
        return observability.runWithSpan(
                "rag.evaluation.run",
                Map.of(),
                "result",
                () -> delegate.getQuestionsAndAnswers()
        );
    }
}
