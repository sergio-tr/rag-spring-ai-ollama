package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import java.util.List;

/**
 * JSON root for suite definition create (POST) and update (PUT) — P35.
 */
public record RuntimeTraceRegressionSuiteDefinitionUpsertRequestDto(
        String name, String description, List<RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto> entries) {}
