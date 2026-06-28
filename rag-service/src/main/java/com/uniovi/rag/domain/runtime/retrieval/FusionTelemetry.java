package com.uniovi.rag.domain.runtime.retrieval;

/** Hybrid fusion summary for diagnostics and exports. */
public record FusionTelemetry(
        String fusionStrategy,
        int preFusionCount,
        int postFusionCount,
        int metadataCandidateCount,
        boolean hybridApplied) {

    public FusionTelemetry {
        fusionStrategy = fusionStrategy == null ? "" : fusionStrategy;
    }
}
