package com.uniovi.rag.application.service.evaluation.corpus;

/**
 * Stable reason codes for Lab evaluation corpus readiness, HTTP errors, and benchmark preflight.
 */
public final class LabCorpusReasonCodes {

    private LabCorpusReasonCodes() {}

    /** Corpus exists but has zero linked documents. */
    public static final String NO_DOCUMENTS = "NO_DOCUMENTS";

    public static final String KB_EMPTY = "KB_EMPTY";

    public static final String KB_NOT_FOUND = "KB_NOT_FOUND";

    /** Alias for missing corpus resource (404). */
    public static final String CORPUS_UNAVAILABLE = "CORPUS_UNAVAILABLE";

    public static final String NO_CORPUS_SELECTED = "NO_CORPUS_SELECTED";

    public static final String NO_READY_DOCUMENTS = "NO_READY_DOCUMENTS";

    public static final String DOCUMENT_PROCESSING_FAILED = "DOCUMENT_PROCESSING_FAILED";

    /** No active index snapshot bound for the evaluation corpus. */
    public static final String NO_ACTIVE_SNAPSHOT = "NO_ACTIVE_SNAPSHOT";

    public static final String NO_COMPATIBLE_SNAPSHOT = "NO_COMPATIBLE_SNAPSHOT";

    public static final String REINDEX_REQUIRED = "REINDEX_REQUIRED";

    public static final String SNAPSHOT_VECTOR_ROWS_MISSING = "SNAPSHOT_VECTOR_ROWS_MISSING";

    public static final String DOCUMENT_IMPORT_NOT_FOUND = "DOCUMENT_IMPORT_NOT_FOUND";

    public static final String DOCUMENT_SCOPE_NOT_SHARED = "DOCUMENT_SCOPE_NOT_SHARED";

    public static final String DOCUMENT_BINARY_MISSING = "DOCUMENT_BINARY_MISSING";

    /** Client-only: persisted corpus id no longer exists on server. */
    public static final String STALE_CORPUS_SELECTION = "STALE_CORPUS_SELECTION";

    /** Could not resolve or persist runtime config snapshot required for corpus index build. */
    public static final String RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE = "RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE";

    /** Alias for {@link #STALE_CORPUS_SELECTION} (plan registry name). */
    public static final String CORPUS_STALE = STALE_CORPUS_SELECTION;

    public static boolean isReasonCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.trim().matches("^[A-Z][A-Z0-9_]+$");
    }
}
