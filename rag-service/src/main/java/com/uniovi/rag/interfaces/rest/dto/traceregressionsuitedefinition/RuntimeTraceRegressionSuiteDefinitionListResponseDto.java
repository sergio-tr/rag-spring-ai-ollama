package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import java.util.List;

public record RuntimeTraceRegressionSuiteDefinitionListResponseDto(List<RuntimeTraceRegressionSuiteDefinitionSummaryDto> definitions) {

    public RuntimeTraceRegressionSuiteDefinitionListResponseDto {
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }
}
