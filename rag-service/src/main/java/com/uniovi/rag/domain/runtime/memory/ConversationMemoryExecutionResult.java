package com.uniovi.rag.domain.runtime.memory;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of executing the canonical memory stage for one orchestrated turn (P12).
 */
public record ConversationMemoryExecutionResult(
        ConversationMemoryOutcome outcome,
        Optional<ConversationMemorySlice> slice,
        boolean condensationAttempted,
        boolean condensationUsed,
        boolean fallbackApplied,
        String finalPlanningInputText,
        List<ExecutionStageTrace> stageTraces) {

    public ConversationMemoryExecutionResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
        slice = slice == null ? Optional.empty() : slice;
        finalPlanningInputText = finalPlanningInputText != null ? finalPlanningInputText : "";
        stageTraces = List.copyOf(Objects.requireNonNull(stageTraces, "stageTraces"));
        if (fallbackApplied && condensationUsed) {
            throw new IllegalArgumentException("fallbackApplied and condensationUsed cannot both be true");
        }
        if (!condensationAttempted && (condensationUsed || fallbackApplied)) {
            throw new IllegalArgumentException("condensationAttempted=false requires condensationUsed=false and fallbackApplied=false");
        }
    }
}

