package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.result.evaluation.EvaluationSummary;
import com.uniovi.rag.application.result.evaluation.JudgeSummarizableRow;
import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.result.evaluation.RagPresetEvaluationBatchResult;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.infrastructure.observability.Loggable;

import java.util.List;
import java.util.function.BiConsumer;

public interface EvaluationService extends Loggable {

    /**
     * Canonical benchmark path: evaluates explicit typed LLM rows.
     *
     * @param itemProgress optional {@code (index1Based, total)} notification before each question
     */
    LlmJudgeEvaluationBatchResult evaluateWithConfigurationForLlmReaderQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<LlmReaderQuestion> questions,
            BiConsumer<Integer, Integer> itemProgress);

    default LlmJudgeEvaluationBatchResult evaluateWithConfigurationForLlmReaderQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<LlmReaderQuestion> questions) {
        return evaluateWithConfigurationForLlmReaderQuestions(customConfig, implementationProperties, questions, null);
    }

    /** Canonical benchmark path: RAG pipeline over explicit typed preset question rows. */
    RagPresetEvaluationBatchResult evaluateWithConfigurationForRagPresetQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<RagPresetQuestion> questions,
            BiConsumer<Integer, Integer> itemProgress);

    default RagPresetEvaluationBatchResult evaluateWithConfigurationForRagPresetQuestions(
            RagFeatureConfiguration customConfig,
            RagImplementationProperties implementationProperties,
            List<RagPresetQuestion> questions) {
        return evaluateWithConfigurationForRagPresetQuestions(customConfig, implementationProperties, questions, null);
    }

    void loadData();

    /** Whether the in-memory evaluation dataset has been loaded for this service instance. */
    boolean isEvaluationDataLoaded();

    /** LLM-as-judge scoring (same template as typed evaluation). */
    String judgeQaAnswer(String question, String goldAnswer, String generatedAnswer);

    /** Builds {@code evaluation_summary} from per-question benchmark rows. */
    EvaluationSummary summarizeJudgeResults(List<? extends JudgeSummarizableRow> resultsForPrompt);
}
