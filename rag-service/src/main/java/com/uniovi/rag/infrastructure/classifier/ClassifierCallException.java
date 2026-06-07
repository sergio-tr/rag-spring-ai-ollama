package com.uniovi.rag.infrastructure.classifier;

/**
 * Structured failure from an outbound classifier-service HTTP call.
 * Mapped by {@link com.uniovi.rag.application.service.runtime.query.DefaultQueryClassifierAdapter}
 * to recoverable {@link com.uniovi.rag.domain.runtime.query.ClassifierStatus} values.
 */
public final class ClassifierCallException extends RuntimeException {

    public enum Kind {
        /** Network, protocol, or upstream outage (includes uvicorn "Invalid HTTP request received."). */
        UNAVAILABLE,
        /** Read/connect timeout. */
        TIMEOUT,
        /** Request contract rejected by classifier-service (400/422 validation). */
        INVALID_REQUEST,
        /** Semantic output invalid (503 invalid label, unknown queryType). */
        INVALID_OUTPUT
    }

    private final Kind kind;
    private final int httpStatus;

    public ClassifierCallException(Kind kind, String message) {
        this(kind, message, 0, null);
    }

    public ClassifierCallException(Kind kind, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public Kind kind() {
        return kind;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
