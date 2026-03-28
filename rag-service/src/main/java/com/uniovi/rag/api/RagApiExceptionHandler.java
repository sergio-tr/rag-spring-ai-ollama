package com.uniovi.rag.api;

import com.uniovi.rag.api.dto.ApiResponse;
import com.uniovi.rag.exception.RagServiceException;
import com.uniovi.rag.model.Loggable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link RagServiceException} (e.g. Ollama unreachable) to {@link ApiResponse} + HTTP status.
 * Other exceptions use Spring Boot default handling unless caught inside services.
 */
@RestControllerAdvice(basePackages = "com.uniovi.rag.controller")
public class RagApiExceptionHandler implements Loggable {

    @ExceptionHandler(RagServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleRagService(RagServiceException ex) {
        log().warn("RAG service error [{}]: {}", ex.getErrorCode(), ex.getPublicMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.fail(
                        ex.getErrorCode().name(),
                        ex.getPublicMessage(),
                        ex.getDetail()));
    }
}
