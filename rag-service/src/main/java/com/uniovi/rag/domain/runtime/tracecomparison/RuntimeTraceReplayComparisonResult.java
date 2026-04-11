package com.uniovi.rag.domain.runtime.tracecomparison;

import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transient P19 comparison result (never persisted).
 */
public record RuntimeTraceReplayComparisonResult(
        UUID userId,
        UUID projectId,
        UUID originalTraceId,
        UUID conversationId,
        UUID messageId,
        RuntimeTraceReplayMode comparisonMode,
        RuntimeTraceReplayComparisonReplayEcho replayRequestEcho,
        RuntimeTraceReplayComparisonOutcome runtimeTraceReplayComparisonOutcome,
        RuntimeTraceReplayOutcome replayOutcome,
        RuntimeTraceReplayAnswerComparisonStatus answerComparisonStatus,
        boolean exactMatch,
        String summary,
        List<RuntimeTraceReplayFieldMismatch> mismatches,
        String originalRouteKind,
        String replayRouteKind,
        String originalWorkflowName,
        String replayWorkflowName) {

    public RuntimeTraceReplayComparisonResult {
        replayRequestEcho = replayRequestEcho != null ? replayRequestEcho : new RuntimeTraceReplayComparisonReplayEcho(Optional.empty(), Optional.empty(), Optional.empty());
        replayOutcome = replayOutcome != null ? replayOutcome : RuntimeTraceReplayOutcome.NOT_ATTEMPTED;
        answerComparisonStatus =
                answerComparisonStatus != null
                        ? answerComparisonStatus
                        : RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT;
        summary = summary != null ? summary : "";
        mismatches = mismatches != null ? List.copyOf(mismatches) : List.of();
        originalRouteKind = originalRouteKind != null ? originalRouteKind : "";
        replayRouteKind = replayRouteKind != null ? replayRouteKind : "";
        originalWorkflowName = originalWorkflowName != null ? originalWorkflowName : "";
        replayWorkflowName = replayWorkflowName != null ? replayWorkflowName : "";
    }
}
