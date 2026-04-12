package com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterunimportpreview;

import com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunDetailDto;

import java.util.List;

/**
 * P45: JSON body for {@code POST …/runtime-trace-regression-suite-runs/import/preview} on success ({@code importable} always {@code true},
 * {@code warnings} empty).
 */
public record RuntimeTraceRegressionSuiteRunImportPreviewResponseDto(
        RuntimeTraceRegressionSuiteRunDetailDto run, boolean importable, List<String> warnings) {

    public RuntimeTraceRegressionSuiteRunImportPreviewResponseDto {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
