package com.uniovi.rag.domain.runtime.tracereplay;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import java.util.Objects;
import java.util.Optional;

/**
 * Transient replay result (P18). Never persisted.
 */
public record RuntimeTraceReplayResult(
        RuntimeTraceReplayOutcome outcome,
        Optional<String> answerText,
        Optional<String> failureDetail,
        Optional<ExecutionTrace> transientReplayTrace) {

    public RuntimeTraceReplayResult {
        answerText = Objects.requireNonNullElseGet(answerText, Optional::empty);
        failureDetail = Objects.requireNonNullElseGet(failureDetail, Optional::empty);
        transientReplayTrace = Objects.requireNonNullElseGet(transientReplayTrace, Optional::empty);
    }

    public static RuntimeTraceReplayResult unsupported(RuntimeTraceReplayOutcome outcome, Optional<String> detail) {
        return new RuntimeTraceReplayResult(outcome, Optional.empty(), detail, Optional.empty());
    }

    public static RuntimeTraceReplayResult success(String answer, ExecutionTrace transientReplayTrace) {
        return new RuntimeTraceReplayResult(
                RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED,
                Optional.ofNullable(answer),
                Optional.empty(),
                Optional.ofNullable(transientReplayTrace));
    }

    public static RuntimeTraceReplayResult failedSafe(String detail) {
        return new RuntimeTraceReplayResult(
                RuntimeTraceReplayOutcome.REPLAY_FAILED_SAFE, Optional.empty(), Optional.ofNullable(detail), Optional.empty());
    }
}
