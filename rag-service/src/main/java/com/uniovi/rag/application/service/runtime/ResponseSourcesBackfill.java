package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds minimal response source maps when workflow retrieval succeeded but sources were not attached. */
public final class ResponseSourcesBackfill {

    private static final int MAX_SOURCES = 8;

    private ResponseSourcesBackfill() {}

    public static List<Map<String, Object>> resolve(RagExecutionResult result) {
        if (result == null) {
            return List.of();
        }
        List<Map<String, Object>> existing = result.responseSources();
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        if (!result.retrievalUsed()) {
            return List.of();
        }
        return result.retrievalDiagnostics().map(ResponseSourcesBackfill::fromDiagnostics).orElse(List.of());
    }

    private static List<Map<String, Object>> fromDiagnostics(RetrievalDiagnostics diagnostics) {
        if (diagnostics == null) {
            return List.of();
        }
        List<String> candidateIds = diagnostics.afterRerankTopCandidateIds();
        if (candidateIds == null || candidateIds.isEmpty()) {
            candidateIds = diagnostics.beforeRerankTopCandidateIds();
        }
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(MAX_SOURCES, candidateIds.size());
        for (int i = 0; i < n; i++) {
            String chunkId = candidateIds.get(i);
            if (chunkId == null || chunkId.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = Map.of("chunkId", chunkId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunkId", chunkId);
            row.put("metadata", metadata);
            out.add(Map.copyOf(row));
        }
        return List.copyOf(out);
    }
}
