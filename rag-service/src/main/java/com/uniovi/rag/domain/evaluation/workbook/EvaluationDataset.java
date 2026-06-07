package com.uniovi.rag.domain.evaluation.workbook;

/**
 * Typed experimental dataset view: kind + parsed workbook content (possibly partial for simple uploads).
 */
public record EvaluationDataset(ExperimentalDatasetType experimentalType, EvaluationWorkbook workbook) {

    public EvaluationDataset {
        if (experimentalType == null) {
            throw new IllegalArgumentException("experimentalType required");
        }
        if (workbook == null) {
            throw new IllegalArgumentException("workbook required");
        }
    }
}
