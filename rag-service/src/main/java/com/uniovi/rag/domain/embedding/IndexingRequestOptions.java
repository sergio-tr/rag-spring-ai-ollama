package com.uniovi.rag.domain.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Indexing / ingest embedding safety knobs. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndexingRequestOptions(
        Integer batchSize, Integer maxInputChars, Boolean normalize, String truncate) {

    public IndexingRequestOptions {
        if (batchSize != null && batchSize <= 0) {
            batchSize = null;
        }
        if (maxInputChars != null && maxInputChars <= 0) {
            maxInputChars = null;
        }
        if (truncate != null && truncate.isBlank()) {
            truncate = null;
        }
    }
}
