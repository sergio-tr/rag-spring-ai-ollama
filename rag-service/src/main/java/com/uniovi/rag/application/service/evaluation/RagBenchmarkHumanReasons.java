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
            case "REINDEX_REQUIRED", "NO_ACTIVE_INDEX", "FEATURE_REQUIRES_REINDEX", "INDEX_REQUIRES_REINDEX" ->
                    "The project index must be rebuilt before this preset can run.";
            case "SNAPSHOT_INCOMPATIBLE", "NO_COMPATIBLE_SNAPSHOT", "SNAPSHOT_CONFIG_MISMATCH" ->
                    "No compatible index snapshot exists for this preset.";
            case "PRESET_NOT_SUPPORTED", "NOT_SUPPORTED" ->
                    "This experimental preset is not supported with the current configuration.";
            case "EMBEDDING_DIMENSION_MISMATCH" ->
                    "The embedding model is not compatible with the vector index.";
            case "EXPERIMENTAL_PRESET_CODES_EMPTY" ->
                    "Select at least one experimental preset (P0–P12) before starting the benchmark.";
            case "UNSUPPORTED_PRESET" -> "The selected experimental preset is not supported.";
            case "PRESET_NOT_LAB_SELECTABLE", "PRESET_NOT_SINGLE_TURN_BENCHMARK" ->
                    "This preset is not available for single-turn Lab benchmarks (use Chat for multi-turn presets).";
            case "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED" ->
                    "Clarification preset (P13) is not supported in the Lab single-turn harness.";
            case "PRESET_CONVERSATIONAL_MEMORY_BENCHMARK_NOT_SUPPORTED" ->
                    "Conversational memory preset (P14) is not supported in the Lab single-turn harness.";
            case "INCOMPATIBLE_FEATURES", "INVALID_RUNTIME_CONFIG", "INVALID_FEATURE_COMBINATION" ->
                    "The runtime configuration combines features that cannot run together.";
            case "USE_ADVISOR_REQUIRES_RETRIEVAL" ->
                    "Advisor requires retrieval to be enabled.";
            case "STRUCTURED_SEARCH_WITH_RETRIEVAL_NOT_SUPPORTED" ->
                    "Structured search materialization cannot be used with retrieval enabled.";
            case "FEATURE_REQUIRES_INDEX" -> "An active vector index is required for the selected preset.";
            case "FEATURE_REQUIRES_SNAPSHOT" -> "A compatible index snapshot is required for the selected preset.";
            case "RUNTIME_FEATURE_NOT_IMPLEMENTED", "RUNTIME_NOT_IMPLEMENTED" ->
                    "A requested runtime feature is not implemented in this deployment.";
            case "CONFIG_VALIDATION_ERROR" -> "Runtime configuration validation failed.";
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
