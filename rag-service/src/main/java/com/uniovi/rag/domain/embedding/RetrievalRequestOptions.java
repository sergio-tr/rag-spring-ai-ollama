package com.uniovi.rag.domain.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Retrieval knobs shared by RAG and embedding evaluation runs. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrievalRequestOptions(Integer topK, Double similarityThreshold, String materializationStrategy) {

    public RetrievalRequestOptions {
        if (topK != null && topK <= 0) {
            topK = null;
        }
        if (materializationStrategy != null && materializationStrategy.isBlank()) {
            materializationStrategy = null;
        }
    }
}
