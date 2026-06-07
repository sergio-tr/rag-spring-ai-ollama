package com.uniovi.rag.domain.evaluation.workbook;

import java.util.Map;

/** Row from {@code embedding_candidates} sheet. */
public record EmbeddingCandidate(
        String candidateId,
        String model,
        String role,
        String priority,
        String expectedFit,
        String profileNotes,
        String protocols,
        Map<String, String> extraColumns) {

    public EmbeddingCandidate {
        if (candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("candidateId required");
        }
        extraColumns = extraColumns != null ? Map.copyOf(extraColumns) : Map.of();
    }
}
