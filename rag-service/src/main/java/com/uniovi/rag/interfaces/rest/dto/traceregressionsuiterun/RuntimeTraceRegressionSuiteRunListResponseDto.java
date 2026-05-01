package com.uniovi.rag.interfaces.rest.dto.traceregressionsuiterun;

import com.uniovi.rag.domain.runtime.traceregressionsuiterun.RuntimeTraceRegressionSuiteRunSummary;

import java.util.ArrayList;
import java.util.List;

/** P42 list response: root key {@code runs} only. */
public record RuntimeTraceRegressionSuiteRunListResponseDto(List<RuntimeTraceRegressionSuiteRunSummaryDto> runs) {

    public RuntimeTraceRegressionSuiteRunListResponseDto {
        runs = List.copyOf(runs);
    }

    public static RuntimeTraceRegressionSuiteRunListResponseDto fromSummaries(
            List<RuntimeTraceRegressionSuiteRunSummary> summaries) {
        List<RuntimeTraceRegressionSuiteRunSummaryDto> dtos = new ArrayList<>(summaries.size());
        for (RuntimeTraceRegressionSuiteRunSummary s : summaries) {
            dtos.add(RuntimeTraceRegressionSuiteRunSummaryDto.fromSummary(s));
        }
        return new RuntimeTraceRegressionSuiteRunListResponseDto(dtos);
    }
}
