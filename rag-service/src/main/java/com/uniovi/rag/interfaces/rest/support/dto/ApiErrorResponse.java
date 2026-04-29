package com.uniovi.rag.interfaces.rest.support.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Canonical JSON error response for API surfaces.
 *
 * <p>Must not include stack traces or raw exception class names. Tokens and secrets must never be logged
 * or returned in this payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<ApiValidationError> validationErrors) {}

