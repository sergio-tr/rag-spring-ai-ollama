package com.uniovi.rag.infrastructure.vector;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Caches {@link PgVectorStore} instances keyed by embedding model id (same JDBC table; dimension enforcement is separate).
 */
@Component
public class PgVectorStoreRegistry {

    private final JdbcTemplate jdbcTemplate;
    private final OllamaEmbeddingModelFactory embeddingModelFactory;
    private final ConcurrentHashMap<String, PgVectorStore> cache = new ConcurrentHashMap<>();

    public PgVectorStoreRegistry(JdbcTemplate jdbcTemplate, OllamaEmbeddingModelFactory embeddingModelFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModelFactory = embeddingModelFactory;
    }

    public PgVectorStore forEmbeddingModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId");
        }
        String key = modelId.trim();
        return cache.computeIfAbsent(key, k -> {
            EmbeddingModel model = embeddingModelFactory.forModel(k);
            return PgVectorStore.builder(jdbcTemplate, model).build();
        });
    }
}
