package com.uniovi.rag.domain.evaluation.workbook;

/** One row for {@link ExperimentalDatasetType#CLASSIFIER_DATASET} (Question + QueryType columns). */
public record ClassifierQuestionRow(String question, String queryTypeLabel) {

    public ClassifierQuestionRow {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question required");
        }
        queryTypeLabel = queryTypeLabel != null ? queryTypeLabel : "";
    }
}
