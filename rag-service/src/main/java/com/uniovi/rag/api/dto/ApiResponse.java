package com.uniovi.rag.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope for RAG HTTP API: every response uses the same top-level shape.
 *
 * @param <T> success payload type (e.g. {@link QuerySuccessPayload})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message, String detail) {
        return new ApiResponse<>(false, null, new ApiErrorBody(code, message, detail));
    }
}
