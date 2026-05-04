package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.observability.Loggable;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface EvaluationService extends Loggable {

    /** @deprecated Removed — use typed benchmark runs ({@code POST …/lab/benchmarks/{kind}/runs}). */
    @Deprecated
    Map<String, Object> evaluate();
    
    /**
     * @param customConfig feature flags (expansion, ner, tools, …)
     * @param implementationProperties implementation selection ({@code rag.query-service-impl}, etc.);
     *        use the application bean to mirror the default runtime
     */
    /** @deprecated Removed — use {@link #evaluateWithConfigurationForLlmReaderQuestions} / {@link #evaluateWithConfigurationForRagPresetQuestions}. */
    @Deprecated
    Map<String, Object> evaluateWithConfiguration(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties);

    /**
     * Canonical benchmark path: evaluates explicit typed LLM rows (no {@link #getQuestionsAndAnswers()}).
     *
     * @param itemProgress optional {@code (index1Based, total)} notification before each question
     */
    Map<String, Object> evaluateWithConfigurationForLlmReaderQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<LlmReaderQuestion> questions,
            BiConsumer<Integer, Integer> itemProgress);

    default Map<String, Object> evaluateWithConfigurationForLlmReaderQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<LlmReaderQuestion> questions) {
        return evaluateWithConfigurationForLlmReaderQuestions(customConfig, implementationProperties, questions, null);
    }

    /**
     * Canonical benchmark path: RAG pipeline over explicit typed preset question rows (no {@link #getQuestionsAndAnswers()}).
     */
    Map<String, Object> evaluateWithConfigurationForRagPresetQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<RagPresetQuestion> questions,
            BiConsumer<Integer, Integer> itemProgress);

    default Map<String, Object> evaluateWithConfigurationForRagPresetQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<RagPresetQuestion> questions) {
        return evaluateWithConfigurationForRagPresetQuestions(customConfig, implementationProperties, questions, null);
    }

    /** @deprecated Removed — combinatorial Map evaluation no longer supported. */
    @Deprecated
    Map<String, Map<String, Object>> evaluateAllConfigurations();

    void loadData();

    /** @deprecated Removed from production — benchmarks use typed datasets only. */
    @Deprecated
    Map<String, String> getQuestionsAndAnswers();

    /** Whether the in-memory evaluation dataset has been loaded for this service instance. */
    boolean isEvaluationDataLoaded();

    /** LLM-as-judge scoring (same template as legacy typed evaluation). */
    String judgeQaAnswer(String question, String goldAnswer, String generatedAnswer);

    /** Builds {@code evaluation_summary} from rows shaped like {@code evaluateWithConfiguration} results. */
    Map<String, Object> summarizeJudgeResults(List<Map<String, Object>> resultsForPrompt);
}
