package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.evaluation.workbook.EvaluationWorkbook;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;

/** Primary row counts per experimental kind for upload responses and listing. */
public final class ExperimentalDatasetMetrics {

    private ExperimentalDatasetMetrics() {}

    public static int primaryRowCount(EvaluationWorkbook wb, ExperimentalDatasetType kind) {
        if (wb == null) {
            return 0;
        }
        return switch (kind) {
            case LLM_MODEL_BASELINE -> wb.llmReaderQuestions().size();
            case EMBEDDING_MODEL_BASELINE -> wb.embeddingRetrievalQueries().size();
            case RAG_PRESET_BENCHMARK -> wb.ragPresetQuestionsEnriched().size();
            case CLASSIFIER_DATASET -> wb.classifierQuestions().size();
            case REFERENCE_BUNDLE -> wb.llmReaderQuestions().size();
        };
    }

    /**
     * Total informative rows across relevant sheets (for display); falls back to primary when single-sheet kinds.
     */
    public static int totalRowCount(EvaluationWorkbook wb, ExperimentalDatasetType kind) {
        if (wb == null) {
            return 0;
        }
        return switch (kind) {
            case LLM_MODEL_BASELINE -> wb.llmReaderQuestions().size();
            case EMBEDDING_MODEL_BASELINE ->
                    wb.embeddingRetrievalQueries().size() + wb.chunkRegistry().size();
            case RAG_PRESET_BENCHMARK -> wb.ragPresetQuestionsEnriched().size() + wb.chunkRegistry().size();
            case CLASSIFIER_DATASET -> wb.classifierQuestions().size();
            case REFERENCE_BUNDLE ->
                    wb.llmReaderQuestions().size()
                            + wb.embeddingRetrievalQueries().size()
                            + wb.ragPresetQuestionsEnriched().size();
        };
    }
}
