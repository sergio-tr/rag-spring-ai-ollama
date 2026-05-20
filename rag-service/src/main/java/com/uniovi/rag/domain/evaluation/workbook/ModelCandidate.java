package com.uniovi.rag.domain.evaluation.workbook;

import java.util.Map;

/** Row from {@code llm_candidates} sheet. */
public record ModelCandidate(
        String candidateId,
        String model,
        String role,
        String priority,
        String expectedFit,
        String hardwareNote,
        String protocols,
        Map<String, String> extraColumns) {

    public ModelCandidate {
        if (candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("candidateId required");
        }
        extraColumns = extraColumns != null ? Map.copyOf(extraColumns) : Map.of();
    }
}
