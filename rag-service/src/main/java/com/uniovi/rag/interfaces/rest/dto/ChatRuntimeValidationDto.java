package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

public record ChatRuntimeValidationDto(
        boolean valid,
        boolean supported,
        List<RuntimeConfigValidationIssueDto> errors,
        List<RuntimeConfigValidationIssueDto> warnings
) {}

