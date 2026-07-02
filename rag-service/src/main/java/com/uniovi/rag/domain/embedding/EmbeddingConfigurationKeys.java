package com.uniovi.rag.domain.embedding;

/** Stable keys for embedding defaults in RAG configuration and Lab runtime payloads. */
public final class EmbeddingConfigurationKeys {

    public static final String EMBEDDING_ENCODING_FORMAT = "embeddingEncodingFormat";
    public static final String EMBEDDING_DIMENSIONS = "embeddingDimensions";
    public static final String EMBEDDING_TIMEOUT_SECONDS = "embeddingTimeoutSeconds";
    public static final String EMBEDDING_BATCH_SIZE = "embeddingBatchSize";
    public static final String EMBEDDING_MAX_INPUT_CHARS = "embeddingMaxInputChars";
    public static final String EMBEDDING_NORMALIZE = "embeddingNormalize";
    public static final String EMBEDDING_TRUNCATE = "embeddingTruncate";

    public static final String RUNTIME_EMBEDDING_OPTIONS = "embeddingOptions";
    public static final String RUNTIME_RETRIEVAL_OPTIONS = "retrievalOptions";
    public static final String RUNTIME_INDEXING_OPTIONS = "indexingOptions";

    public static final String ERROR_DIMENSIONS_UNSUPPORTED = "EMBEDDING_DIMENSIONS_UNSUPPORTED";
    public static final String ERROR_ENCODING_FORMAT_UNSUPPORTED = "EMBEDDING_ENCODING_FORMAT_UNSUPPORTED";
    public static final String ERROR_NORMALIZE_UNSUPPORTED = "EMBEDDING_NORMALIZE_UNSUPPORTED";
    public static final String ERROR_TRUNCATE_UNSUPPORTED = "EMBEDDING_TRUNCATE_UNSUPPORTED";

    private EmbeddingConfigurationKeys() {}
}
