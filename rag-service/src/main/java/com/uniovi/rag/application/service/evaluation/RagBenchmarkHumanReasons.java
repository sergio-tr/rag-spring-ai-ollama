package com.uniovi.rag.application.service.evaluation;

/**
 * Human-readable explanations for benchmark skip / not-supported codes (stored in result rows and exports).
 */
public final class RagBenchmarkHumanReasons {

    private RagBenchmarkHumanReasons() {}

    public static String humanize(String codeOrMessage) {
        if (codeOrMessage == null || codeOrMessage.isBlank()) {
            return "This benchmark item could not be run.";
        }
        String raw = codeOrMessage.trim();
        String code = raw.contains(":") ? raw.substring(0, raw.indexOf(':')).trim() : raw;
        String upper = code.toUpperCase();

        return switch (upper) {
            case "CORPUS_EMPTY", "KB_EMPTY", "NO_DOCUMENTS" ->
                    "The knowledge base has no documents to retrieve from.";
            case "NO_READY_DOCUMENTS" ->
                    "No documents are ready in the knowledge base yet.";
            case "MODEL_UNAVAILABLE" ->
                    "The configured model is not available in Ollama.";
            case "REINDEX_FAILED", "AUTO_REINDEX_FAILED" ->
                    "The index could not be prepared for this preset.";
            case "REINDEX_IN_PROGRESS" -> "Another index operation is already in progress.";
            case "REINDEX_REQUIRED", "NO_ACTIVE_INDEX" ->
                    "The project index must be rebuilt before this preset can run.";
            case "SNAPSHOT_INCOMPATIBLE", "NO_COMPATIBLE_SNAPSHOT" ->
                    "No compatible index snapshot exists for this preset.";
            case "PRESET_NOT_SUPPORTED", "NOT_SUPPORTED" ->
                    "This experimental preset is not supported with the current configuration.";
            case "INDEX_REQUIRES_REINDEX" ->
                    "The index must be reindexed before this item can execute.";
            case "EMBEDDING_DIMENSION_MISMATCH" ->
                    "The embedding model is not compatible with the vector index.";
            case "DUPLICATE_FILE" -> "Duplicate document in the knowledge base.";
            case "KB_NOT_FOUND", "CORPUS_UNAVAILABLE" ->
                    "The selected knowledge base does not exist or is not accessible.";
            case "DOCUMENT_IMPORT_NOT_FOUND" ->
                    "The selected project document could not be found.";
            case "DOCUMENT_SCOPE_NOT_SHARED" ->
                    "Only project-shared documents can be imported into the Lab knowledge base.";
            case "DOCUMENT_BINARY_MISSING" ->
                    "The document has no stored file; re-upload it in the project first.";
            case "NO_ACTIVE_SNAPSHOT" ->
                    "No active index snapshot exists for this knowledge base yet.";
            case "STALE_CORPUS_SELECTION" ->
                    "The saved knowledge base selection is no longer valid.";
            case "BACKEND_ERROR" -> "A server error occurred. Check logs and retry.";
            default -> raw.length() > 120 ? raw.substring(0, 117) + "..." : raw;
        };
    }
}
