package com.uniovi.rag.application.service.evaluation.lab;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Summary of classpath corpus bootstrap for {@code evaluation_run.aggregates_json}. */
public record LabCorpusBootstrapResult(
        boolean enabled,
        String classpathDocsLocation,
        String corpusScope,
        int discoveredCount,
        int createdCount,
        int reusedCount,
        int readyCount,
        int failedCount,
        int skippedCount,
        List<UUID> documentIds,
        List<String> errors,
        Instant startedAt,
        Instant completedAt) {

    /** Alias for {@link #classpathDocsLocation()} for callers that refer to the resolved scan pattern. */
    public String location() {
        return classpathDocsLocation;
    }

    public Map<String, Object> toAggregatesMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("classpathDocsLocation", classpathDocsLocation);
        m.put("corpusScope", corpusScope);
        // Stable Lab audit keys (camelCase).
        m.put("bootstrapDocumentsFound", discoveredCount);
        m.put("bootstrapDocumentsCreated", createdCount);
        m.put("bootstrapDocumentsSkipped", skippedCount);
        m.put("bootstrapDocumentsFailed", failedCount);
        m.put("bootstrapDocumentsReused", reusedCount);
        m.put("bootstrapDocumentsReady", readyCount);
        boolean ok = enabled && readyCount > 0 && failedCount == 0;
        m.put("bootstrapSuccess", ok);
        // Backward-compatible keys (older UI/clients).
        m.put("location", classpathDocsLocation);
        m.put("discoveredCount", discoveredCount);
        m.put("createdCount", createdCount);
        m.put("reusedCount", reusedCount);
        m.put("readyCount", readyCount);
        m.put("failedCount", failedCount);
        m.put("skippedCount", skippedCount);
        m.put(
                "documentIds",
                documentIds != null ? documentIds.stream().map(UUID::toString).toList() : List.of());
        m.put("errors", errors != null ? errors : List.of());
        m.put("startedAt", startedAt != null ? startedAt.toString() : null);
        m.put("completedAt", completedAt != null ? completedAt.toString() : null);
        return m;
    }
}
