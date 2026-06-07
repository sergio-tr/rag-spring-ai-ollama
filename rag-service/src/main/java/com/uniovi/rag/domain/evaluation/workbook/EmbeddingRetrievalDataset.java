package com.uniovi.rag.domain.evaluation.workbook;

import java.util.List;

/**
 * Typed embedding-retrieval benchmark slice: queries plus optional chunk corpus metadata from the workbook.
 */
public record EmbeddingRetrievalDataset(
        List<EmbeddingRetrievalQuery> queries,
        List<ChunkRegistryEntry> chunkRegistry,
        List<CorpusDocument> corpusDocuments) {

    public EmbeddingRetrievalDataset {
        queries = queries != null ? List.copyOf(queries) : List.of();
        chunkRegistry = chunkRegistry != null ? List.copyOf(chunkRegistry) : List.of();
        corpusDocuments = corpusDocuments != null ? List.copyOf(corpusDocuments) : List.of();
    }
}
