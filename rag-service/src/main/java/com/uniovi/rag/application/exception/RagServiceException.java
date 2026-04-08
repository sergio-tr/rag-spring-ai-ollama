package com.uniovi.rag.application.exception;

import com.uniovi.rag.domain.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the RAG pipeline cannot complete due to infrastructure (e.g. Ollama unreachable).
 * Mapped to HTTP by {@link com.uniovi.rag.interfaces.rest.support.RagApiExceptionHandler}.
 */
public class RagServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final String publicMessage;
    private final String detail;

    public RagServiceException(ErrorCode errorCode, HttpStatus httpStatus, String publicMessage, String detail, Throwable cause) {
        super(cause != null ? cause.getMessage() : publicMessage, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.publicMessage = publicMessage;
        this.detail = detail;
    }

    public static RagServiceException llmUnavailable(Throwable cause) {
        return new RagServiceException(
                ErrorCode.LLM_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "The AI inference service (Ollama) is not reachable. "
                        + "Ensure Ollama is running and that spring.ai.ollama.base-url / OLLAMA_BASE_URL points to it "
                        + "(e.g. http://ollama:11434 in Docker with compose.ollama-local-gpu.yml).",
                cause != null ? cause.getClass().getSimpleName() : null,
                cause
        );
    }

    /**
     * Ollama is reachable but a configured chat or embedding model is not installed ({@code /api/embed} or chat 404).
     */
    public static RagServiceException ollamaModelNotInstalled(Throwable cause) {
        return new RagServiceException(
                ErrorCode.LLM_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "A required Ollama model is missing (chat and/or embedding). "
                        + "On the Ollama host run: ollama pull for the names in "
                        + "spring.ai.ollama.chat.model and spring.ai.ollama.embedding.model, "
                        + "or ensure rag.ollama.auto-pull-enabled completed at startup.",
                cause != null ? cause.getMessage() : null,
                cause
        );
    }

    public static RagServiceException unsupportedRuntimeConfiguration(String detail) {
        return new RagServiceException(
                ErrorCode.UNSUPPORTED_RUNTIME_CONFIGURATION,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "unsupported-runtime-configuration: " + (detail != null ? detail : "configuration not supported in runtime"),
                detail,
                null);
    }

    public static RagServiceException knowledgeSnapshotUnavailable() {
        return new RagServiceException(
                ErrorCode.KNOWLEDGE_SNAPSHOT_UNAVAILABLE,
                HttpStatus.UNPROCESSABLE_ENTITY,
                "knowledge-snapshot-unavailable: no ACTIVE knowledge index snapshot for this request scope",
                null,
                null);
    }

    /** Hybrid sparse leg failed; dense-only fallback is not applied. */
    public static RagServiceException hybridSparseRetrievalFailed(Throwable cause) {
        return new RagServiceException(
                ErrorCode.INTERNAL_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "hybrid sparse retrieval failed",
                cause != null ? cause.getMessage() : null,
                cause);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getPublicMessage() {
        return publicMessage;
    }

    public String getDetail() {
        return detail;
    }
}
