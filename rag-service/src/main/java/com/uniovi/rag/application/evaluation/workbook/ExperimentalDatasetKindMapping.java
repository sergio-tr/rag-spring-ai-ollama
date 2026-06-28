package com.uniovi.rag.application.evaluation.workbook;

import com.uniovi.rag.domain.EvaluationDatasetType;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;

import java.util.Locale;
import java.util.Optional;

/**
 * Maps URL template segments and upload {@code datasetType} strings to {@link ExperimentalDatasetType}.
 */
public final class ExperimentalDatasetKindMapping {

    private ExperimentalDatasetKindMapping() {}

    /** Template path segment under {@code GET …/lab/dataset-templates/{kind}}. */
    public static Optional<String> templateKindSegment(ExperimentalDatasetType type) {
        return switch (type) {
            case LLM_MODEL_BASELINE -> Optional.of("llm-model-baseline");
            case EMBEDDING_MODEL_BASELINE -> Optional.of("embedding-baseline");
            case RAG_PRESET_BENCHMARK -> Optional.of("rag-preset-benchmark");
            case CLASSIFIER_DATASET -> Optional.of("classifier-question-querytype");
            case REFERENCE_BUNDLE -> Optional.empty();
        };
    }

    public static Optional<ExperimentalDatasetType> parseTemplatePathSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return Optional.empty();
        }
        String s = segment.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "llm-model-baseline" -> Optional.of(ExperimentalDatasetType.LLM_MODEL_BASELINE);
            case "embedding-baseline" -> Optional.of(ExperimentalDatasetType.EMBEDDING_MODEL_BASELINE);
            case "rag-preset-benchmark" -> Optional.of(ExperimentalDatasetType.RAG_PRESET_BENCHMARK);
            case "classifier-question-querytype" -> Optional.of(ExperimentalDatasetType.CLASSIFIER_DATASET);
            default -> Optional.empty();
        };
    }

    /**
     * Parses multipart/query {@code datasetType}: enum constant ({@code LLM_MODEL_BASELINE}) or template segment
     * ({@code llm-model-baseline}).
     */
    public static Optional<ExperimentalDatasetType> parseUploadDatasetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String t = raw.trim();
        Optional<ExperimentalDatasetType> fromSegment = parseTemplatePathSegment(t);
        if (fromSegment.isPresent()) {
            return fromSegment;
        }
        String normalized = t.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Optional.of(ExperimentalDatasetType.valueOf(normalized));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Coarse persisted {@link EvaluationDatasetType} for orchestrator compatibility (plan §10.1 minimum). */
    public static EvaluationDatasetType toPersistedCoarseType(ExperimentalDatasetType experimental) {
        return switch (experimental) {
            case LLM_MODEL_BASELINE -> EvaluationDatasetType.LLM_ONLY;
            case EMBEDDING_MODEL_BASELINE, RAG_PRESET_BENCHMARK -> EvaluationDatasetType.RAG;
            case CLASSIFIER_DATASET -> EvaluationDatasetType.CLASSIFIER;
            case REFERENCE_BUNDLE -> EvaluationDatasetType.RAG;
        };
    }
}
