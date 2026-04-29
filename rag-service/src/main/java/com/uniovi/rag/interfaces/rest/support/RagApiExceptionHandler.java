package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link RagServiceException} (e.g. Ollama unreachable) to the canonical JSON error contract.
 */
@RestControllerAdvice(basePackages = "com.uniovi.rag.interfaces.rest")
public class RagApiExceptionHandler implements Loggable {

    @ExceptionHandler(RagServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleRagService(RagServiceException ex, HttpServletRequest request) {
        log().warn("RAG service error [{}]: {}", ex.getErrorCode(), ex.getPublicMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(new ApiErrorResponse(
                        Instant.now(),
                        ex.getHttpStatus().value(),
                        ex.getErrorCode().name(),
                        ex.getPublicMessage(),
                        request != null ? request.getRequestURI() : null,
                        request != null ? request.getHeader("X-Request-Id") : null,
                        null));
    }
}
