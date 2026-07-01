package com.uniovi.rag.domain.llm.catalog;

/** Stable machine-readable codes for configured vs runtime model availability (Phase 6). */
public final class LlmModelReasonCodes {

    private LlmModelReasonCodes() {}

    public static final String LLM_MODEL_UNAVAILABLE = "LLM_MODEL_UNAVAILABLE";
    public static final String LLM_MODEL_NOT_CONFIGURED = "LLM_MODEL_NOT_CONFIGURED";
    public static final String EMBEDDING_DIMENSION_MISMATCH = "EMBEDDING_DIMENSION_MISMATCH";
    public static final String EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE =
            "EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE";
    public static final String LLM_PROVIDER_UNAVAILABLE = "LLM_PROVIDER_UNAVAILABLE";

    public static String format(String code, String message) {
        if (code == null || code.isBlank()) {
            return message != null ? message : "";
        }
        if (message == null || message.isBlank()) {
            return code;
        }
        return code + ": " + message;
    }

    public static String reasonCodePrefix(String formattedMessage) {
        if (formattedMessage == null || formattedMessage.isBlank()) {
            return null;
        }
        int colon = formattedMessage.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return formattedMessage.substring(0, colon).trim();
    }
}
