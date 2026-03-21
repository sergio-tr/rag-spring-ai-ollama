package com.uniovi.rag.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error payload when {@link ApiResponse#success()} is false.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message, String detail) {
}
