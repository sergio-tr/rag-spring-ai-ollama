package com.uniovi.rag.domain.runtime.judge;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JudgeEvaluation(
        JudgeOutcome outcome,
        Optional<Double> score,
        String feedback,
        List<ExecutionStageTrace> stageTraces
) {
    public JudgeEvaluation {
        Objects.requireNonNull(outcome, "outcome");
        score = Objects.requireNonNullElseGet(score, Optional::empty);
        feedback = feedback == null ? "" : feedback;
        stageTraces = List.copyOf(stageTraces == null ? List.of() : stageTraces);
    }
}

