package com.uniovi.rag.interfaces.rest.dto;

public record RuntimeConfigValidationIssueDto(
        String code,
        String field,
        String message,
        String severity
) {}

