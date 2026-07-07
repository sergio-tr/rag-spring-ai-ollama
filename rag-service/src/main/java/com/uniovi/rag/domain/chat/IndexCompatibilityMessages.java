package com.uniovi.rag.domain.chat;

/**
 * User-facing index compatibility copy shared by preset catalog and runtime validation.
 */
public final class IndexCompatibilityMessages {

    public static final String NO_ACTIVE_INDEX = "No active index";
    public static final String METADATA_SUPPORT_REQUIRED = "Requires metadata-aware index capability";
    public static final String STRUCTURED_SEARCH_RETRIEVAL_UNSUPPORTED =
            "Structured-search projects do not support retrieval-based RAG presets";

    private IndexCompatibilityMessages() {}

    public static String forRequiredMaterializationStrategy(String requiredStrategy) {
        if (requiredStrategy == null || requiredStrategy.isBlank()) {
            return "Requires a compatible index profile";
        }
        return switch (requiredStrategy.trim().toUpperCase()) {
            case "DOCUMENT_LEVEL" -> "Requires DOCUMENT_LEVEL index";
            case "CHUNK_LEVEL" -> "Requires CHUNK_LEVEL index";
            case "HYBRID" -> "Requires HYBRID index";
            default -> "Requires a compatible index profile";
        };
    }
}
