package com.uniovi.rag.application.service.chat;

import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import java.util.List;
import org.springframework.http.HttpStatus;

public class RuntimeConfigurationInvalidException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<RuntimeConfigValidationIssueDto> issues;

    public RuntimeConfigurationInvalidException(
            String code,
            String message,
            HttpStatus status,
            List<RuntimeConfigValidationIssueDto> issues) {
        super(message);
        this.code = code;
        this.status = status != null ? status : HttpStatus.UNPROCESSABLE_ENTITY;
        this.issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public List<RuntimeConfigValidationIssueDto> issues() {
        return issues;
    }
}
