package com.uniovi.rag.domain.knowledge;

/**
 * How the knowledge pipeline materializes corpus state for indexing (not retrieval).
 */
public enum MaterializationStrategy {
    DOCUMENT_LEVEL,
    CHUNK_LEVEL,
    HYBRID,
    STRUCTURED_SEARCH
}
