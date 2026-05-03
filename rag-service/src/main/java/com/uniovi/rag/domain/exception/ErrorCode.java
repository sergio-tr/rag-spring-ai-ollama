package com.uniovi.rag.domain.exception;

/**
 * Stable machine-readable codes for API error responses ({@code success: false}).
 */
public enum ErrorCode {
    LLM_UNAVAILABLE,
    UNSUPPORTED_RUNTIME_CONFIGURATION,
    KNOWLEDGE_SNAPSHOT_UNAVAILABLE,
    /** Conversation {@code documentFilter} IDs do not match any document in the project. */
    CHAT_DOCUMENT_SCOPE_EMPTY,
    /** Conversation {@code documentFilter} contains invalid identifiers. */
    CHAT_DOCUMENT_FILTER_INVALID,
    INTERNAL_ERROR
}
