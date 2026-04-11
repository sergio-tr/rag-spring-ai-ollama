package com.uniovi.rag.interfaces.rest.dto.tracecomparison;

import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bounded projection of {@link RuntimeTraceReplayComparisonResult} for P20 GET responses.
 */
public record RuntimeTraceReplayComparisonResponseDto(
        UUID originalTraceId,
        UUID conversationId,
        UUID messageId,
        String comparisonMode,
        String comparisonOutcome,
        String replayOutcome,
        String answerComparisonStatus,
        boolean exactMatch,
        String summary,
        String originalRouteKind,
        String replayRouteKind,
        String originalWorkflowName,
        String replayWorkflowName,
        List<RuntimeTraceReplayComparisonMismatchDto> mismatches) {

    static final int MAX_SUMMARY_CHARS = 512;
    static final int MAX_MISMATCHES = 50;

    public static RuntimeTraceReplayComparisonResponseDto fromRuntimeTraceReplayComparisonResult(
            RuntimeTraceReplayComparisonResult result) {
        List<RuntimeTraceReplayFieldMismatch> raw = result.mismatches();
        List<RuntimeTraceReplayComparisonMismatchDto> out = new ArrayList<>(Math.min(raw.size(), MAX_MISMATCHES));
        for (int i = 0; i < raw.size() && i < MAX_MISMATCHES; i++) {
            out.add(RuntimeTraceReplayComparisonMismatchDto.fromFieldMismatch(raw.get(i)));
        }
        return new RuntimeTraceReplayComparisonResponseDto(
                result.originalTraceId(),
                result.conversationId(),
                result.messageId(),
                result.comparisonMode().name(),
                result.runtimeTraceReplayComparisonOutcome().name(),
                result.replayOutcome().name(),
                result.answerComparisonStatus().name(),
                result.exactMatch(),
                truncateSummary(result.summary()),
                result.originalRouteKind(),
                result.replayRouteKind(),
                result.originalWorkflowName(),
                result.replayWorkflowName(),
                List.copyOf(out));
    }

    private static String truncateSummary(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_SUMMARY_CHARS) {
            return s;
        }
        return s.substring(0, MAX_SUMMARY_CHARS);
    }
}
