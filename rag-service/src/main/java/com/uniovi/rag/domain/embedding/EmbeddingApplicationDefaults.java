package com.uniovi.rag.domain.embedding;

import java.util.LinkedHashMap;
import java.util.Map;

/** Built-in system embedding + retrieval defaults (EMB-1 wide_chunk winner, applied EMB-2). */
public final class EmbeddingApplicationDefaults {

    public static final String EMBEDDING_MODEL = "bge-m3";
    public static final int EMBEDDING_DIMENSIONS = 1024;
    public static final int RETRIEVAL_TOP_K = 12;
    public static final double SIMILARITY_THRESHOLD = 0.25;
    public static final String MATERIALIZATION_STRATEGY = "CHUNK_LEVEL";
    public static final boolean EMBEDDING_NORMALIZE = true;
    public static final int EMBEDDING_BATCH_SIZE = 32;
    public static final int EMBEDDING_TIMEOUT_SECONDS = 60;

    /** Fast alternate (documented only — no runtime storage). */
    public static final String FAST_ALTERNATE_EMBEDDING_MODEL =
            "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest";

    /** Quality alternate (documented only — no runtime storage). */
    public static final String QUALITY_ALTERNATE_EMBEDDING_MODEL = "snowflake-arctic-embed2";

    private EmbeddingApplicationDefaults() {}

    /** Keys merged into {@code default_system_configuration.values} by V82 migration. */
    public static Map<String, Object> systemConfigurationValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("topK", RETRIEVAL_TOP_K);
        values.put("similarityThreshold", SIMILARITY_THRESHOLD);
        values.put("materializationStrategy", MATERIALIZATION_STRATEGY);
        values.put("embeddingModel", EMBEDDING_MODEL);
        values.put(EmbeddingConfigurationKeys.EMBEDDING_DIMENSIONS, EMBEDDING_DIMENSIONS);
        values.put(EmbeddingConfigurationKeys.EMBEDDING_NORMALIZE, EMBEDDING_NORMALIZE);
        values.put(EmbeddingConfigurationKeys.EMBEDDING_BATCH_SIZE, EMBEDDING_BATCH_SIZE);
        values.put(EmbeddingConfigurationKeys.EMBEDDING_TIMEOUT_SECONDS, EMBEDDING_TIMEOUT_SECONDS);
        return Map.copyOf(values);
    }
}
