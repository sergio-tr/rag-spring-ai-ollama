package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link RagServiceException} (e.g. Ollama unreachable) to the canonical JSON error contract.
 */
@RestControllerAdvice(basePackages = "com.uniovi.rag.interfaces.rest")
public class RagApiExceptionHandler implements Loggable {

    @ExceptionHandler(LlmProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleLlmProvider(LlmProviderException ex, HttpServletRequest request) {
        log()
                .warn(
                        "LLM provider error [{}] kind={} provider={} operation={} model={} baseUrl={}: {}",
                        ex.errorCode(),
                        ex.failureKind(),
                        ex.provider(),
                        ex.operation(),
                        ex.model(),
                        ex.baseUrl(),
                        ex.publicMessage());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("provider", ex.provider() != null ? ex.provider().name() : null);
        details.put("operation", ex.operation());
        if (ex.detail() != null && !ex.detail().isBlank()) {
            details.put("detail", ex.detail());
        }
        return ResponseEntity
                .status(ex.httpStatus())
                .body(new ApiErrorResponse(
                        Instant.now(),
                        ex.httpStatus().value(),
                        ex.errorCode().name(),
                        ex.publicMessage(),
                        request != null ? request.getRequestURI() : null,
                        request != null ? request.getHeader("X-Request-Id") : null,
                        null,
                        details));
    }

    @ExceptionHandler(RagServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleRagService(RagServiceException ex, HttpServletRequest request) {
        log().warn("RAG service error [{}]: {}", ex.getErrorCode(), ex.getPublicMessage());
        Map<String, Object> details = null;
        if (ex.getErrorCode() == ErrorCode.UNSUPPORTED_RUNTIME_CONFIGURATION) {
            details = new LinkedHashMap<>();
            details.put("details", ex.getDetail());
        }
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(new ApiErrorResponse(
                        Instant.now(),
                        ex.getHttpStatus().value(),
                        ex.getErrorCode().name(),
                        ex.getPublicMessage(),
                        request != null ? request.getRequestURI() : null,
                        request != null ? request.getHeader("X-Request-Id") : null,
                        null,
                        details));
    }
}
