package com.uniovi.rag.interfaces.rest.support.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        List<ApiValidationError> validationErrors,
        Map<String, Object> details) {

    /**
     * Backward/forward compatibility for API clients that expect an envelope-style response:
     * {@code { success: false, error: { code, message, detail? } }}.
     *
     * <p>We keep the existing top-level fields (status/code/message/...) so existing consumers and
     * tests remain stable, while also providing the envelope shape for newer clients.
     */
    @JsonProperty("success")
    public boolean success() {
        return false;
    }

    @JsonProperty("error")
    public ApiErrorBody error() {
        return new ApiErrorBody(code, message, details);
    }
}

