package com.uniovi.rag.application.service.knowledge;

/**
 * Stable ingestion failure codes stored in {@code knowledge_document.error_message} (prefix before {@code :}).
 * API status remains {@link com.uniovi.rag.domain.ProjectDocumentStatus#ERROR} (UI may label as FAILED).
 */
public final class DocumentIngestionFailureCodes {

    public static final String FAILED_STALE_INGESTION = "FAILED_STALE_INGESTION";
    public static final String FAILED_TIMEOUT = "FAILED_TIMEOUT";
    public static final String FAILED_EMBEDDING = "FAILED_EMBEDDING";
    public static final String FAILED_PARSING = "FAILED_PARSING";
    public static final String FAILED_INDEX = "FAILED_INDEX";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String FAILED_GENERIC = "FAILED_GENERIC";
    public static final String FAILED_INCOMPATIBLE_INDEX = "FAILED_INCOMPATIBLE_INDEX";

    private DocumentIngestionFailureCodes() {}

    public static String format(String code, String detail) {
        if (code == null || code.isBlank()) {
            return detail != null ? detail : FAILED_GENERIC;
        }
        if (detail == null || detail.isBlank()) {
            return code;
        }
        return code + ": " + detail.trim();
    }

    public static String classify(Throwable error) {
        if (error == null) {
            return format(FAILED_GENERIC, "Unknown error");
        }
        String msg = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        String upper = msg.toUpperCase();

        if (upper.contains("DUPLICATE")) {
            return format(DUPLICATE, msg);
        }
        if (upper.contains("NO COMPATIBLE VECTOR INDEX")) {
            return format(FAILED_INCOMPATIBLE_INDEX, msg);
        }
        if (upper.contains("EMBEDDING") || upper.contains("CONTEXT LIMIT")) {
            return format(FAILED_EMBEDDING, msg);
        }
        if (upper.contains("PARSE") || upper.contains("COULD NOT PARSE")) {
            return format(FAILED_PARSING, msg);
        }
        if (upper.contains("INDEX") && (upper.contains("FAIL") || upper.contains("ERROR"))) {
            return format(FAILED_INDEX, msg);
        }
        if (upper.contains("TIMED OUT") || upper.contains("TIMEOUT")) {
            return format(FAILED_TIMEOUT, msg);
        }
        if (upper.contains("UNSUPPORTED")) {
            return format(FAILED_PARSING, msg);
        }
        return format(FAILED_GENERIC, msg);
    }
}
