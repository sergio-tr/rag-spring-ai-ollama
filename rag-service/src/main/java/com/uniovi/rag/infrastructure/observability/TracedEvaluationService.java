package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.evaluation.EvaluationSummary;
import com.uniovi.rag.application.result.evaluation.JudgeSummarizableRow;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.RagPresetEvaluationBatchResult;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.application.service.evaluation.EvaluationService;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Decorator that adds tracing and metrics to any {@link EvaluationService}.
 * Each operation is wrapped in a span and optional counter so evaluation
 * runs are visible in traces and metrics.
 */
public final class TracedEvaluationService implements EvaluationService {

    private static final String SPAN_RESULT_KEY = "result";

    /** Micrometer / tracing tag for the logical operation name. */
    private static final String TAG_OPERATION = "operation";

    /** Counter name for evaluation-layer calls (aligned with other {@code rag.*.calls} metrics). */
    private static final String METRIC_EVALUATION_CALLS = "rag.evaluation.calls";

    /** Span name for evaluation execution (single constant for Sonar duplicate-literal rule). */
    private static final String SPAN_EVALUATION_RUN = "rag.evaluation.run";

    private final EvaluationService delegate;
    private final ObservabilitySupport observability;

    public TracedEvaluationService(EvaluationService delegate, ObservabilitySupport observability) {
        this.delegate = delegate;
        this.observability = observability;
    }

    @Override
    public LlmJudgeEvaluationBatchResult evaluateWithConfigurationForLlmReaderQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<LlmReaderQuestion> questions,
            BiConsumer<Integer, Integer> itemProgress) {
        observability.recordCounter(METRIC_EVALUATION_CALLS, TAG_OPERATION, "evaluateWithConfigurationForLlmReaderQuestions");
        return observability.recordTimer("rag.evaluation.evaluateTypedLlm", () ->
                observability.runWithSpan(
                        SPAN_EVALUATION_RUN,
                        Map.of(TAG_OPERATION, "typed_llm"),
                        SPAN_RESULT_KEY,
                        () ->
                                delegate.evaluateWithConfigurationForLlmReaderQuestions(
                                        customConfig, implementationProperties, questions, itemProgress)));
    }

    @Override
    public RagPresetEvaluationBatchResult evaluateWithConfigurationForRagPresetQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<RagPresetQuestion> questions,
            BiConsumer<Integer, Integer> itemProgress) {
        observability.recordCounter(METRIC_EVALUATION_CALLS, TAG_OPERATION, "evaluateWithConfigurationForRagPresetQuestions");
        return observability.recordTimer("rag.evaluation.evaluateTypedRag", () ->
                observability.runWithSpan(
                        SPAN_EVALUATION_RUN,
                        Map.of(TAG_OPERATION, "typed_rag_preset"),
                        SPAN_RESULT_KEY,
                        () ->
                                delegate.evaluateWithConfigurationForRagPresetQuestions(
                                        customConfig, implementationProperties, questions, itemProgress)));
    }

    @Override
    public void loadData() {
        observability.recordCounter(METRIC_EVALUATION_CALLS, TAG_OPERATION, "loadData");
        observability.recordTimer("rag.evaluation.loadData", () -> {
            observability.runWithSpan(
                    SPAN_EVALUATION_RUN, Map.of(TAG_OPERATION, "loadData"), delegate::loadData);
            return null;
        });
    }

    @Override
    public boolean isEvaluationDataLoaded() {
        return delegate.isEvaluationDataLoaded();
    }

    @Override
    public String judgeQaAnswer(String question, String goldAnswer, String generatedAnswer) {
        observability.recordCounter(METRIC_EVALUATION_CALLS, TAG_OPERATION, "judgeQaAnswer");
        return observability.recordTimer(
                "rag.evaluation.judgeQaAnswer",
                () -> delegate.judgeQaAnswer(question, goldAnswer, generatedAnswer));
    }

    @Override
    public EvaluationSummary summarizeJudgeResults(List<? extends JudgeSummarizableRow> resultsForPrompt) {
        observability.recordCounter(METRIC_EVALUATION_CALLS, TAG_OPERATION, "summarizeJudgeResults");
        return observability.recordTimer(
                "rag.evaluation.summarizeJudgeResults",
                () -> delegate.summarizeJudgeResults(resultsForPrompt));
    }
}
