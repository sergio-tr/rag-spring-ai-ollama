package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.domain.llm.LlmProvider;

/** Factory methods for remote LLM provider failures (non-configuration). */
public final class LlmRemoteFailures {

    private LlmRemoteFailures() {}

    public static LlmProviderException unauthorized(
            LlmProvider provider, String operation, String model, String baseUrl, int statusCode) {
        return new LlmProviderException(
                LlmFailureKind.UNAUTHORIZED,
                provider,
                operation,
                model,
                baseUrl,
                "LLM credentials rejected (HTTP "
                        + statusCode
                        + "): verify API key env var and proxy permissions for /v1/chat/completions",
                "httpStatus=" + statusCode,
                null);
    }

    public static LlmProviderException endpointNotFound(
            LlmProvider provider, String operation, String model, String baseUrl, int statusCode) {
        return new LlmProviderException(
                LlmFailureKind.ENDPOINT_NOT_FOUND,
                provider,
                operation,
                model,
                baseUrl,
                "LLM chat endpoint not found (HTTP "
                        + statusCode
                        + "): verify base URL and path /v1/chat/completions",
                "httpStatus=" + statusCode,
                null);
    }

    public static LlmProviderException invalidModel(
            LlmProvider provider, String operation, String model, String baseUrl, String detail) {
        return new LlmProviderException(
                LlmFailureKind.INVALID_MODEL,
                provider,
                operation,
                model,
                baseUrl,
                "LLM model rejected by provider: " + (detail != null ? detail : model),
                detail,
                null);
    }

    public static LlmProviderException connectionFailed(
            LlmProvider provider, String operation, String model, String baseUrl, Throwable cause) {
        return new LlmProviderException(
                LlmFailureKind.CONNECTION_FAILED,
                provider,
                operation,
                model,
                baseUrl,
                "Could not connect to LLM endpoint (provider=" + provider + ")",
                cause != null ? cause.getMessage() : null,
                cause);
    }

    public static LlmProviderException remoteHttp(
            LlmProvider provider,
            String operation,
            String model,
            String baseUrl,
            int statusCode,
            String bodySnippet) {
        return new LlmProviderException(
                LlmFailureKind.REMOTE_HTTP,
                provider,
                operation,
                model,
                baseUrl,
                "LLM provider returned HTTP " + statusCode,
                bodySnippet,
                null);
    }

    public static LlmProviderException invalidResponse(
            LlmProvider provider, String operation, String model, String baseUrl, String detail) {
        return new LlmProviderException(
                LlmFailureKind.REMOTE_HTTP,
                provider,
                operation,
                model,
                baseUrl,
                "LLM provider returned an unexpected response: " + detail,
                detail,
                null);
    }

    public static LlmProviderException ollamaUnavailable(String operation, String model, String baseUrl) {
        return new LlmProviderException(
                LlmFailureKind.UNAVAILABLE,
                LlmProvider.OLLAMA_NATIVE,
                operation,
                model,
                baseUrl,
                "Ollama is not reachable at the configured base URL",
                null,
                null);
    }
}
