package com.uniovi.rag.domain.knowledge;

/**
 * Values stored in {@code reindex_event.reason} (VARCHAR).
 */
public final class ReindexEventReason {

    public static final String CONFIG_SOFT = "CONFIG_SOFT";
    public static final String CONFIG_HARD = "CONFIG_HARD";
    public static final String DOCUMENT_UPLOAD = "DOCUMENT_UPLOAD";
    public static final String OPERATOR_REQUEST = "OPERATOR_REQUEST";

    private ReindexEventReason() {}
}
