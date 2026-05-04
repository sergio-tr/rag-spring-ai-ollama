package com.uniovi.rag.domain.evaluation.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Immutable embedding configuration captured at benchmark start (logical snapshot; runtime may still use a single
 * global {@code EmbeddingModel} bean — see module README limitations).
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
