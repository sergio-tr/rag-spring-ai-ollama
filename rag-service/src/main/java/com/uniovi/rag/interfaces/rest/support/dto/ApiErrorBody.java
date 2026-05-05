package com.uniovi.rag.interfaces.rest.support.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Error payload when {@link ApiResponse#success()} is false.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorBody(String code, String message, Map<String, Object> detail) {
}
