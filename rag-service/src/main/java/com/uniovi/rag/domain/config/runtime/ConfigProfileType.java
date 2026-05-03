package com.uniovi.rag.domain.config.runtime;

/**
 * Typed configuration slice stored in {@code config_profile.payload} (validated in application layer).
 */
public enum ConfigProfileType {
    METADATA,
    CHUNKING,
    EMBEDDING,
    INDEX,
    INGESTION_LLM,
    PROMPT_TECHNICAL,
    RAG_RUNTIME_FLAGS
}
