package com.uniovi.rag.application.service.knowledge;

/**
 * Normalizes raw ingestion failure messages into stable, human-readable codes for API consumers.
 */
public final class DocumentIngestionHumanErrors {

    public static final String UNSUPPORTED_FILE = "Unsupported file type for knowledge ingestion.";
    public static final String PARSE_ERROR = "Could not parse document content.";
    public static final String EMBEDDING_ERROR = "Embedding step failed for this document.";
    public static final String INCOMPATIBLE_INDEX_ERROR =
            "No compatible vector index for the selected embedding model. Reindex the project or align the embedding model with the configured catalog.";
    public static final String INDEX_ERROR = "Indexing step failed for this document.";
    public static final String DUPLICATE = "Duplicate document (same content or name and size).";
    public static final String EMPTY_FILE = "File is empty.";
    public static final String INGESTION_TIMEOUT = "Ingestion timed out; retry or re-upload.";
    public static final String GENERIC = "Document ingestion failed.";

    private DocumentIngestionHumanErrors() {}

    public static String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String msg = raw.trim();
        String upper = msg.toUpperCase();

        if (upper.contains("DUPLICATE_FILE") || upper.contains("DUPLICATE")) {
            return DUPLICATE;
        }
        if (upper.contains("UNSUPPORTED") || upper.contains("UNSUPPORTED_TYPE") || upper.contains("415")) {
            return UNSUPPORTED_FILE;
        }
        if (upper.contains("PARSE") || upper.contains("COULD NOT PARSE")) {
            return PARSE_ERROR;
        }
        if (upper.contains("NO COMPATIBLE VECTOR INDEX") || upper.contains("FAILED_INCOMPATIBLE_INDEX")) {
            return INCOMPATIBLE_INDEX_ERROR;
        }
        if (upper.contains("EMBED") || upper.contains("CONTEXT LIMIT")) {
            return EMBEDDING_ERROR;
        }
        if (upper.contains("INDEX") && (upper.contains("FAIL") || upper.contains("ERROR"))) {
            return INDEX_ERROR;
        }
        if (upper.contains("EMPTY") || upper.equals("EMPTY_BYTES")) {
            return EMPTY_FILE;
        }
        if (upper.contains("FAILED_STALE_INGESTION")) {
            return INGESTION_TIMEOUT;
        }
        if (upper.contains("FAILED_TIMEOUT")) {
            return INGESTION_TIMEOUT;
        }
        if (upper.contains("FAILED_EMBEDDING")) {
            return EMBEDDING_ERROR;
        }
        if (upper.contains("FAILED_PARSING")) {
            return PARSE_ERROR;
        }
        if (upper.contains("FAILED_INDEX")) {
            return INDEX_ERROR;
        }
        if (upper.contains("TIMED OUT") || upper.contains("WATCHDOG")) {
            return INGESTION_TIMEOUT;
        }
        if (msg.length() > 240) {
            return msg.substring(0, 237) + "...";
        }
        return msg;
    }
}
