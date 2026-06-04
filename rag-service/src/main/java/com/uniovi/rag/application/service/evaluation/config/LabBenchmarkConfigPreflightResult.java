package com.uniovi.rag.application.service.evaluation.config;

import java.util.List;
import java.util.Map;

/** Snapshot of a successful Lab benchmark config preflight (stored on {@code evaluation_run.aggregates_json}). */
public record LabBenchmarkConfigPreflightResult(
        boolean passed,
        String primaryCode,
        List<String> requestedPresetCodes,
        String embeddingModelId,
        boolean autoReindex,
        boolean strictIndexCheck,
        Map<String, Object> details) {

    public Map<String, Object> toAggregatesMap() {
        return Map.of(
                "passed",
                passed,
                "primaryCode",
                primaryCode != null ? primaryCode : "OK",
                "requestedPresetCodes",
                requestedPresetCodes != null ? requestedPresetCodes : List.of(),
                "embeddingModelId",
                embeddingModelId != null ? embeddingModelId : "",
                "autoReindex",
                autoReindex,
                "strictIndexCheck",
                strictIndexCheck,
                "details",
                details != null ? details : Map.of());
    }
}
