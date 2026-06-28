package com.uniovi.rag.interfaces.rest.dto;

import java.util.List;

public record RuntimeCompatibilityDto(
        boolean valid,
        boolean supported,
        String selectedWorkflow,
        List<RuntimeConfigValidationIssueDto> blockingIssues,
        List<RuntimeConfigValidationIssueDto> warnings
) {}
