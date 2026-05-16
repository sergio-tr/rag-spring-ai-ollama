package com.uniovi.rag.domain.evaluation.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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
        List<String> unsupportedFields) {

    public EmbeddingExperimentalSnapshot {
        unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
    }
}
