package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.application.result.runtime.RuntimeConfigValidationIssue;
import java.util.List;

/**
 * Raised when persisted or requested chat runtime configuration fails validation.
 * HTTP status is mapped in {@link com.uniovi.rag.interfaces.rest.support.ApiGlobalExceptionHandler}.
 */
public class RuntimeConfigurationInvalidException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final List<RuntimeConfigValidationIssue> issues;

    public RuntimeConfigurationInvalidException(
            String code, String message, int httpStatus, List<RuntimeConfigValidationIssue> issues) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus > 0 ? httpStatus : 422;
        this.issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public List<RuntimeConfigValidationIssue> issues() {
        return issues;
    }
}
