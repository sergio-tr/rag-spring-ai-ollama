package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import java.util.List;

/** Bounded HTTP projection of {@link RuntimeTraceRegressionSuiteResult} (P31). */
public record RuntimeTraceRegressionSuiteResponseDto(
        String suiteOutcome,
        RuntimeTraceRegressionSuiteSummaryDto summary,
        List<RuntimeTraceRegressionSuiteEntryResponseDto> entries) {

    private static final int MAX_ENTRIES = 20;

    public static RuntimeTraceRegressionSuiteResponseDto fromResult(RuntimeTraceRegressionSuiteResult result) {
        List<RuntimeTraceRegressionSuiteEntryResponseDto> list =
                result.entryResults().stream()
                        .map(RuntimeTraceRegressionSuiteEntryResponseDto::fromEntry)
                        .limit(MAX_ENTRIES)
                        .toList();
        return new RuntimeTraceRegressionSuiteResponseDto(
                result.suiteOutcome().name(),
                RuntimeTraceRegressionSuiteSummaryDto.fromSummary(result.summary()),
                list);
    }
}
