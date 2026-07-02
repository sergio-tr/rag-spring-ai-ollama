package com.uniovi.rag.domain.config;

/** Keys for retrieval parameters and preset lock metadata in configuration JSON. */
public final class RetrievalParameterKeys {

    public static final String TOP_K = "topK";
    public static final String SIMILARITY_THRESHOLD = "similarityThreshold";
    public static final String LOCK_RETRIEVAL_PARAMETERS = "lockRetrievalParameters";
    public static final String RETRIEVAL_PARAMETER_POLICY = "retrievalParameterPolicy";

    private RetrievalParameterKeys() {}

    public static boolean isRetrievalParameterKey(String key) {
        return TOP_K.equals(key) || SIMILARITY_THRESHOLD.equals(key);
    }

    public static boolean isPolicyMetadataKey(String key) {
        return LOCK_RETRIEVAL_PARAMETERS.equals(key) || RETRIEVAL_PARAMETER_POLICY.equals(key);
    }
}
