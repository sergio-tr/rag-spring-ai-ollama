package com.uniovi.rag.domain.runtime.retrieval;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;

import java.util.List;
import java.util.Objects;

public record CuratedContextSet(
        List<RetrievalCandidate> finalCandidates,
        String promptContextText,
        CompressionOutcome compression,
        List<String> traceNotes,
        RetrievalDiagnostics diagnostics,
        List<RerankOutcome> rerankOutcomes,
        List<ExecutionStageTrace> retrievalStageTraces) {

    public CuratedContextSet {
        finalCandidates = List.copyOf(Objects.requireNonNull(finalCandidates, "finalCandidates"));
        promptContextText = promptContextText != null ? promptContextText : "";
        compression = Objects.requireNonNull(compression, "compression");
        traceNotes = List.copyOf(Objects.requireNonNull(traceNotes, "traceNotes"));
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        rerankOutcomes = List.copyOf(Objects.requireNonNull(rerankOutcomes, "rerankOutcomes"));
        retrievalStageTraces = List.copyOf(Objects.requireNonNull(retrievalStageTraces, "retrievalStageTraces"));
    }
}
