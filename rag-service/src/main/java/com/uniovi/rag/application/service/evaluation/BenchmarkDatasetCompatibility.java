package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maps persisted dataset metadata to benchmark kinds for orchestration-time validation (Phase 4 bridge).
 */
public final class BenchmarkDatasetCompatibility {

    private BenchmarkDatasetCompatibility() {}

    public static ExperimentalDatasetType resolveExperimentalType(EvaluationDatasetEntity dataset) {
        if (dataset.getExperimentalKind() != null && !dataset.getExperimentalKind().isBlank()) {
            try {
                return ExperimentalDatasetType.valueOf(dataset.getExperimentalKind().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown experimental_kind on evaluation_dataset");
            }
        }
        if (dataset.getType() == EvaluationDatasetType.LLM_ONLY) {
            return ExperimentalDatasetType.LLM_MODEL_BASELINE;
        }
        if (dataset.getType() == EvaluationDatasetType.RAG) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dataset missing experimental_kind for coarse type RAG; pick a typed experimental dataset or re-upload.");
        }
        if (dataset.getType() == EvaluationDatasetType.CLASSIFIER) {
            return ExperimentalDatasetType.CLASSIFIER_DATASET;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported evaluation_dataset.type");
    }

    public static boolean compatible(ExperimentalDatasetType experimental, BenchmarkKind kind) {
        return switch (kind) {
            case LLM_JUDGE_QA ->
                    experimental == ExperimentalDatasetType.LLM_MODEL_BASELINE
                            || experimental == ExperimentalDatasetType.REFERENCE_BUNDLE;
            case EMBEDDING_RETRIEVAL ->
                    experimental == ExperimentalDatasetType.EMBEDDING_MODEL_BASELINE
                            || experimental == ExperimentalDatasetType.REFERENCE_BUNDLE;
            case RAG_PRESET_END_TO_END ->
                    experimental == ExperimentalDatasetType.RAG_PRESET_BENCHMARK
                            || experimental == ExperimentalDatasetType.REFERENCE_BUNDLE;
            case CLASSIFIER_METRICS -> experimental == ExperimentalDatasetType.CLASSIFIER_DATASET;
        };
    }
}
