package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;
import java.util.Map;

public record RuntimeConfigValidateResponse(
        boolean valid,
        boolean supported,
        Map<String, Object> effectiveConfig,
        List<RuntimeConfigValidationIssueDto> errors,
        List<RuntimeConfigValidationIssueDto> warnings,
        String selectedWorkflow
) {}

