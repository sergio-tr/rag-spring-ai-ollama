package com.uniovi.rag.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Physical pgvector constraints and retrieval strictness (independent of per-request {@link com.uniovi.rag.domain.runtime.RagConfig}).
 */
@ConfigurationProperties(prefix = "rag.vector")
public record RagVectorProperties(
        /** Must match {@code vector_store.embedding} typmod (see Flyway V1). */
        @DefaultValue("1024") int storeEmbeddingDimension,
        /**
         * When true, dense retrieval refuses anonymous embedding routing: the active knowledge snapshot must supply
         * {@code embeddingModelId} so query vectors use the same Ollama model as indexing.
         */
        @DefaultValue("true") boolean requireSnapshotEmbeddingModelId) {}
