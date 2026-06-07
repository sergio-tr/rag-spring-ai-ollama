package com.uniovi.rag.application.service.evaluation;

/**
 * Stable reason codes for Lab RAG preset benchmark orchestration (async job and runtime state).
 *
 * <p>Distinct from {@link com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes} and
 * {@link com.uniovi.rag.application.service.evaluation.config.LabRuntimeConfigReasonCodes}.
 */
public final class LabRagReasonCodes {

    private LabRagReasonCodes() {}

    public static final String LAB_RAG_CORPUS_MISSING = "LAB_RAG_CORPUS_MISSING";

    public static final String LAB_RAG_PRESET_MISSING = "LAB_RAG_PRESET_MISSING";

    public static final String LAB_RAG_CONFIG_MISSING = "LAB_RAG_CONFIG_MISSING";

    public static final String LAB_RAG_INDEX_PREPARATION_FAILED = "LAB_RAG_INDEX_PREPARATION_FAILED";

    public static final String LAB_RAG_RUNTIME_STATE_INVALID = "LAB_RAG_RUNTIME_STATE_INVALID";

    public static final String LAB_RAG_DATASET_EMPTY = "LAB_RAG_DATASET_EMPTY";

    public static boolean isLabRagReasonCode(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String code = value.contains(":") ? value.substring(0, value.indexOf(':')).trim() : value.trim();
        return code.startsWith("LAB_RAG_") && code.matches("^[A-Z][A-Z0-9_]+$");
    }
}
