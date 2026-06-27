package com.uniovi.rag.infrastructure.llm.openaicompat;

/**
 * Thrown when OpenAI-compatible / LiteLLM chat calls fail. Not mapped to HTTP until RAG integration.
 */
public class OpenAiCompatibleLlmException extends RuntimeException {

    private final OpenAiCompatibleLlmFailureKind kind;

    public OpenAiCompatibleLlmException(OpenAiCompatibleLlmFailureKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public OpenAiCompatibleLlmException(OpenAiCompatibleLlmFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public OpenAiCompatibleLlmFailureKind kind() {
        return kind;
    }

    public static OpenAiCompatibleLlmException missingConfiguration(String detail) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.MISCONFIGURED,
                "OpenAI-compatible LLM is not configured: " + detail);
    }

    public static OpenAiCompatibleLlmException missingApiKey(String envVarName) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.MISCONFIGURED,
                "API key environment variable is not set or empty: " + envVarName);
    }

    public static OpenAiCompatibleLlmException unauthorized(int statusCode) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.UNAUTHORIZED,
                "OpenAI-compatible chat returned HTTP "
                        + statusCode
                        + ": invalid credentials or API key without permission for /v1/chat/completions");
    }

    public static OpenAiCompatibleLlmException endpointNotFound(String url, int statusCode) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.ENDPOINT_NOT_FOUND,
                "OpenAI-compatible chat endpoint not found (HTTP "
                        + statusCode
                        + "): verify base URL and path /v1/chat/completions at "
                        + url);
    }

    public static OpenAiCompatibleLlmException timeout(Throwable cause) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.TIMEOUT,
                "OpenAI-compatible chat request timed out",
                cause);
    }

    public static OpenAiCompatibleLlmException connectionFailed(Throwable cause) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.CONNECTION_FAILED,
                "Could not connect to OpenAI-compatible chat endpoint",
                cause);
    }

    public static OpenAiCompatibleLlmException invalidResponse(String detail) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.INVALID_RESPONSE,
                "OpenAI-compatible chat returned an unexpected response: " + detail);
    }

    public static OpenAiCompatibleLlmException invalidModel(String detail) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.INVALID_MODEL, "OpenAI-compatible model rejected: " + detail);
    }

    public static OpenAiCompatibleLlmException httpError(int statusCode, String bodySnippet) {
        return new OpenAiCompatibleLlmException(
                OpenAiCompatibleLlmFailureKind.HTTP_ERROR,
                "OpenAI-compatible chat failed with HTTP " + statusCode + ": " + bodySnippet);
    }
}
