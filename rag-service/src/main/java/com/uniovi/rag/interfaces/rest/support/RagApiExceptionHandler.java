package com.uniovi.rag.interfaces.rest.support;

import com.uniovi.rag.application.exception.RagServiceException;
import com.uniovi.rag.domain.config.capability.Capability;
import com.uniovi.rag.domain.exception.ErrorCode;
import com.uniovi.rag.infrastructure.observability.Loggable;
import com.uniovi.rag.interfaces.rest.support.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Map<String, Object> details = null;
        if (ex.getErrorCode() == ErrorCode.UNSUPPORTED_RUNTIME_CONFIGURATION) {
            details = new LinkedHashMap<>();
            details.put("details", ex.getDetail());
            // Best-effort hints for UI: capabilities that are known to be blocked in WorkflowSelector.
            // (We cannot always identify the presetId at this layer; PATCH validation covers that path.)
            details.put(
                    "unsupportedCapabilities",
                    List.of(
                            Capability.REASONING.name(),
                            Capability.RANKER.name(),
                            Capability.POST_RETRIEVAL.name()));
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
