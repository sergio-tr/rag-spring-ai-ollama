package com.uniovi.rag.domain.runtime.functioncalling;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@link com.uniovi.rag.application.service.runtime.functioncalling.FunctionCallingStrategy#tryExecute}
 * when FC was attempted.
 */
public record FunctionCallingExecutionResult(
        FunctionCallingOutcome outcome,
        boolean success,
        Optional<DeterministicToolKind> selectedToolKind,
        String answerText,
        Map<String, Object> normalizedPayload,
        List<String> traceNotes,
        boolean shortCircuited,
        List<ExecutionStageTrace> stageTraces) {

    public FunctionCallingExecutionResult {
        traceNotes = List.copyOf(Objects.requireNonNull(traceNotes, "traceNotes"));
        stageTraces = List.copyOf(Objects.requireNonNull(stageTraces, "stageTraces"));
        answerText = answerText != null ? answerText : "";
        normalizedPayload = Map.copyOf(Objects.requireNonNull(normalizedPayload, "normalizedPayload"));
        selectedToolKind = Objects.requireNonNullElseGet(selectedToolKind, Optional::empty);
    }
}
