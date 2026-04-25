package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinitionimportpreview;

import com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition.RuntimeTraceRegressionSuiteDefinitionDetailDto;

import java.util.List;

/**
 * P40: JSON body for {@code POST …/import/preview} on success ({@code importable} always {@code true}, {@code warnings} empty in this phase).
 */
public record RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto(
        RuntimeTraceRegressionSuiteDefinitionDetailDto definition, boolean importable, List<String> warnings) {

    public RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
