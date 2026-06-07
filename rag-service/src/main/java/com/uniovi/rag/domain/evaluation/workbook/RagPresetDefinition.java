package com.uniovi.rag.domain.evaluation.workbook;

import java.util.Optional;

/** Row from {@code rag_preset_catalog_P0_P14} sheet. */
public record RagPresetDefinition(
        RagExperimentalPresetCode presetId,
        String family,
        String name,
        String retrieval,
        String queryUnderstanding,
        String tools,
        String memory,
        String judges,
        String mainOrComplement,
        String objective,
        String datasetPolicy) {

    public RagPresetDefinition {
        if (presetId == null) {
            throw new IllegalArgumentException("presetId required");
        }
    }

    public static Optional<RagExperimentalPresetCode> parsePresetCell(String raw) {
        return RagExperimentalPresetCode.tryParse(raw);
    }
}
