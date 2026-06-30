package com.uniovi.rag.domain.evaluation.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable embedding configuration captured at benchmark start (logical snapshot aligned with
 * {@code evaluation_run.embedding_model_id} / {@code knowledge_index_snapshot}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingExperimentalSnapshot(
        String model,
        Integer dimension,
        Boolean normalize,
        String queryPrefix,
        String passagePrefix,
        Integer batchSize,
        String truncateStrategy,
        String chatProvider,
        String embeddingProvider,
        Map<String, String> fieldSources,
        List<String> unsupportedFields) {

    public EmbeddingExperimentalSnapshot {
        unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
        fieldSources = fieldSources == null ? Map.of() : Map.copyOf(fieldSources);
    }

    /** Returns a copy with dimension replaced (e.g. after index compatibility alignment). */
    public EmbeddingExperimentalSnapshot withDimension(int dimension, ExperimentalSnapshotFieldSource source) {
        Map<String, String> sources = new LinkedHashMap<>(fieldSources);
        sources.put("dimension", source.name());
        return new EmbeddingExperimentalSnapshot(
                model,
                dimension,
                normalize,
                queryPrefix,
                passagePrefix,
                batchSize,
                truncateStrategy,
                chatProvider,
                embeddingProvider,
                sources,
                unsupportedFields);
    }
}
