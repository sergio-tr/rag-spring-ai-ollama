package com.uniovi.rag.domain.exception;

/**
 * Stable machine-readable codes for API error responses ({@code success: false}).
 */
public enum ErrorCode {
    LLM_UNAVAILABLE,
    /** LLM rejected the request due to context window limits (prompt too large). */
    LLM_CONTEXT_LIMIT_EXCEEDED,
    /** Resolved LLM configuration is invalid or incomplete. */
    LLM_MISCONFIGURED,
    /** LLM remote call exceeded the configured timeout. */
    LLM_TIMEOUT,
    /** LLM credentials rejected (HTTP 401/403). */
    LLM_UNAUTHORIZED,
    /** LLM provider returned an error (endpoint, model, HTTP). */
    LLM_PROVIDER_ERROR,
    /** Model provider is unreachable or not serving requests. */
    MODEL_PROVIDER_UNAVAILABLE,
    /** Network/connection failure reaching the model provider. */
    MODEL_UNREACHABLE,
    /** Configured model id is not available on the provider. */
    MODEL_NOT_FOUND,
    /** Provider rejected credentials (HTTP 401/403). */
    MODEL_AUTH_FAILED,
    /** Model call exceeded the configured timeout. */
    MODEL_TIMEOUT,
    /** Model/provider configuration is invalid or incomplete. */
    MODEL_CONFIG_INVALID,
    /** Embedding model is unavailable for the operation. */
    EMBEDDING_MODEL_UNAVAILABLE,
    /** Chat model is unavailable for the operation. */
    CHAT_MODEL_UNAVAILABLE,
    /** Judge/secondary LLM is unavailable for the operation. */
    JUDGE_MODEL_UNAVAILABLE,
    /** Task-specific secondary LLM is unavailable. */
    SECONDARY_MODEL_UNAVAILABLE,
    /** Embedding output dimensions do not match the vector index. */
    MODEL_DIMENSION_MISMATCH,
    /** Embedding is not supported for the resolved provider. */
    UNSUPPORTED_EMBEDDING_PROVIDER,
    UNSUPPORTED_RUNTIME_CONFIGURATION,
    KNOWLEDGE_SNAPSHOT_UNAVAILABLE,
    /** Conversation {@code documentFilter} IDs do not match any document in the project. */
    CHAT_DOCUMENT_SCOPE_EMPTY,
    /** Conversation {@code documentFilter} contains invalid identifiers. */
    CHAT_DOCUMENT_FILTER_INVALID,
    INTERNAL_ERROR
}
