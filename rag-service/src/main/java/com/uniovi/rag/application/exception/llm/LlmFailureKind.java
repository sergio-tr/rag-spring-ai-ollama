package com.uniovi.rag.application.exception.llm;

/** Stable failure category for LLM provider operations (logging and API error codes). */
public enum LlmFailureKind {
    CONFIGURATION,
    TIMEOUT,
    UNAUTHORIZED,
    ENDPOINT_NOT_FOUND,
    CONNECTION_FAILED,
    INVALID_MODEL,
    REMOTE_HTTP,
    UNSUPPORTED_EMBEDDING,
    UNAVAILABLE
}
