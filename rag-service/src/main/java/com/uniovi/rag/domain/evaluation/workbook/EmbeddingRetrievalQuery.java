package com.uniovi.rag.domain.evaluation.workbook;

import com.uniovi.rag.domain.model.QueryType;

import java.util.List;
import java.util.Optional;

/** Row from {@code embedding_retrieval_queries} sheet. */
public record EmbeddingRetrievalQuery(
        String id,
        String query,
        String queryVariantType,
        Optional<QueryType> queryType,
        Optional<DifficultyLevel> difficulty,
        String expectedAnswer,
        List<String> goldDocumentIds,
        List<String> goldChunkIds,
        String mustRetrieveAny,
        String mustRetrieveAll,
        String notes) {

    public EmbeddingRetrievalQuery {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }
        query = query != null ? query : "";
        goldDocumentIds = goldDocumentIds != null ? List.copyOf(goldDocumentIds) : List.of();
        goldChunkIds = goldChunkIds != null ? List.copyOf(goldChunkIds) : List.of();
    }
}
