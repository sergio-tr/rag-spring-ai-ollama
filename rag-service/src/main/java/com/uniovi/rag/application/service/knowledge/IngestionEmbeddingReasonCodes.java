package com.uniovi.rag.application.service.knowledge;

/** Stable reason codes for project document ingestion embedding compatibility failures. */
public final class IngestionEmbeddingReasonCodes {

    public static final String PROJECT_INDEX_PROFILE_MISMATCH = "PROJECT_INDEX_PROFILE_MISMATCH";
    public static final String NO_COMPATIBLE_VECTOR_INDEX = "NO_COMPATIBLE_VECTOR_INDEX";
    public static final String EMBEDDING_MODEL_ALIAS_MISMATCH = "EMBEDDING_MODEL_ALIAS_MISMATCH";
    public static final String REINDEX_REQUIRED = "REINDEX_REQUIRED";

    private IngestionEmbeddingReasonCodes() {}
}
