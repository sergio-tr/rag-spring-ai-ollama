package com.uniovi.rag.domain.exception;

/**
 * Stable machine-readable codes for API error responses ({@code success: false}).
 */
public enum ErrorCode {
    LLM_UNAVAILABLE,
    UNSUPPORTED_RUNTIME_CONFIGURATION,
    KNOWLEDGE_SNAPSHOT_UNAVAILABLE,
    INTERNAL_ERROR
}
