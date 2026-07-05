package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.domain.llm.LlmProvider;
import org.springframework.http.HttpStatus;

/**
 * Base for LLM provider failures exposed to application and REST layers.
 * Never carries API keys or Authorization headers.
 */
public class LlmProviderException extends RuntimeException {

    private final LlmFailureKind failureKind;
    private final LlmProvider provider;
    private final String operation;
    private final String model;
    private final String baseUrl;
    private final String publicMessage;
    private final String detail;

    protected LlmProviderException(
            LlmFailureKind failureKind,
            LlmProvider provider,
            String operation,
            String model,
            String baseUrl,
            String publicMessage,
            String detail,
            Throwable cause) {
        super(publicMessage, cause);
        this.failureKind = failureKind;
        this.provider = provider;
        this.operation = operation;
        this.model = model;
        this.baseUrl = baseUrl;
        this.publicMessage = publicMessage;
        this.detail = detail;
    }

    public LlmFailureKind failureKind() {
        return failureKind;
    }

    public LlmProvider provider() {
        return provider;
    }

    public String operation() {
        return operation;
    }

    public String model() {
        return model;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String publicMessage() {
        return publicMessage;
    }

    public String detail() {
        return detail;
    }

    public ErrorCode errorCode() {
        return operationAwareErrorCode(failureKind, operation);
    }

    /** Whether the client may retry after fixing transient provider issues. */
    public boolean retryable() {
        return switch (failureKind) {
            case TIMEOUT, CONNECTION_FAILED, UNAVAILABLE, REMOTE_HTTP -> true;
            case CONFIGURATION, UNSUPPORTED_EMBEDDING, UNAUTHORIZED, ENDPOINT_NOT_FOUND, INVALID_MODEL ->
                    false;
        };
    }

    static ErrorCode operationAwareErrorCode(LlmFailureKind kind, String operation) {
        String op = operation != null ? operation.toLowerCase() : "";
        boolean embeddingOp = op.contains("embedding") || op.contains("indexing");
        boolean judgeOp = op.contains("judge");
        boolean chatOp = op.contains("chat") || op.contains("benchmark-llm") || op.contains("benchmark-rag");
        return switch (kind) {
            case CONFIGURATION, UNSUPPORTED_EMBEDDING -> ErrorCode.MODEL_CONFIG_INVALID;
            case TIMEOUT -> ErrorCode.MODEL_TIMEOUT;
            case UNAUTHORIZED -> ErrorCode.MODEL_AUTH_FAILED;
            case UNAVAILABLE, CONNECTION_FAILED -> ErrorCode.MODEL_PROVIDER_UNAVAILABLE;
            case ENDPOINT_NOT_FOUND -> ErrorCode.MODEL_UNREACHABLE;
            case INVALID_MODEL -> {
                if (embeddingOp) {
                    yield ErrorCode.EMBEDDING_MODEL_UNAVAILABLE;
                }
                if (judgeOp) {
                    yield ErrorCode.JUDGE_MODEL_UNAVAILABLE;
                }
                if (chatOp) {
                    yield ErrorCode.CHAT_MODEL_UNAVAILABLE;
                }
                yield ErrorCode.MODEL_NOT_FOUND;
            }
            case REMOTE_HTTP -> {
                if (embeddingOp) {
                    yield ErrorCode.EMBEDDING_MODEL_UNAVAILABLE;
                }
                if (judgeOp) {
                    yield ErrorCode.JUDGE_MODEL_UNAVAILABLE;
                }
                if (chatOp) {
                    yield ErrorCode.CHAT_MODEL_UNAVAILABLE;
                }
                yield ErrorCode.LLM_PROVIDER_ERROR;
            }
        };
    }

    public HttpStatus httpStatus() {
        return switch (failureKind) {
            case CONFIGURATION, UNSUPPORTED_EMBEDDING, INVALID_MODEL -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case ENDPOINT_NOT_FOUND -> HttpStatus.BAD_GATEWAY;
            case CONNECTION_FAILED, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case REMOTE_HTTP -> HttpStatus.BAD_GATEWAY;
        };
    }
}
