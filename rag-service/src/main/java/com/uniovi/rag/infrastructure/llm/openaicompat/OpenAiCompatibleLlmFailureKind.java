package com.uniovi.rag.infrastructure.llm.openaicompat;

/** Failure categories for {@link OpenAiCompatibleLlmException}. */
public enum OpenAiCompatibleLlmFailureKind {
    MISCONFIGURED,
    UNAUTHORIZED,
    ENDPOINT_NOT_FOUND,
    TIMEOUT,
    CONNECTION_FAILED,
    INVALID_RESPONSE,
    INVALID_MODEL,
    UNSUPPORTED_PARAMS,
    HTTP_ERROR
}
