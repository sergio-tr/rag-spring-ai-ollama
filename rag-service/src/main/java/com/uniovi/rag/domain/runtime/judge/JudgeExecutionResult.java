package com.uniovi.rag.domain.runtime.judge;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;

import java.util.List;
import java.util.Objects;

public record JudgeExecutionResult(
        boolean judgeAttempted,
        JudgeOutcome judgeOutcome,
        boolean retryRequested,
        boolean retryAttempted,
        boolean retrySucceeded,
        String finalAnswerText,
        boolean finalAnswerFromRetry,
        List<ExecutionStageTrace> stageTraces
) {
    public JudgeExecutionResult {
        Objects.requireNonNull(judgeOutcome, "judgeOutcome");
        finalAnswerText = finalAnswerText == null ? "" : finalAnswerText;
        stageTraces = List.copyOf(stageTraces == null ? List.of() : stageTraces);
    }
}

