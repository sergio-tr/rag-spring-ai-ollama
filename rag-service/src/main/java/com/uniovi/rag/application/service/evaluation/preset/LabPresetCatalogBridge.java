package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves workbook preset catalog rows for Lab benchmark orchestration, with a narrow fallback to the
 * canonical experimental preset catalog when the workbook has no row for a lab-selectable preset.
 */
public final class LabPresetCatalogBridge {

    private LabPresetCatalogBridge() {}

    /**
     * Returns the workbook definition when present; otherwise a synthetic definition derived from the canonical
     * catalog when the preset is allowed in single-turn Lab benchmarks.
     */
    public static Optional<RagPresetDefinition> resolve(
            RagExperimentalPresetCode preset, Map<RagExperimentalPresetCode, RagPresetDefinition> workbookByPreset) {
        if (preset == null) {
            return Optional.empty();
        }
        RagPresetDefinition workbook = workbookByPreset != null ? workbookByPreset.get(preset) : null;
        if (workbook != null) {
            return Optional.of(workbook);
        }
        if (!ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(preset)) {
            return Optional.empty();
        }
        if (ExperimentalPresetBenchmarkGate.blockReason(preset).isPresent()) {
            return Optional.empty();
        }
        try {
            ExperimentalPresetCanonicalCatalog.require(preset);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.of(syntheticFromCanonical(preset, workbookByPreset));
    }

    private static RagPresetDefinition syntheticFromCanonical(
            RagExperimentalPresetCode preset, Map<RagExperimentalPresetCode, RagPresetDefinition> workbookByPreset) {
        ExperimentalPresetCanonicalCatalog.CanonicalPreset canon = ExperimentalPresetCanonicalCatalog.require(preset);
        RagPresetDefinition parentTemplate =
                canon.parent() != null && workbookByPreset != null ? workbookByPreset.get(canon.parent()) : null;
        if (parentTemplate != null) {
            return new RagPresetDefinition(
                    preset,
                    parentTemplate.family(),
                    preset.name(),
                    parentTemplate.retrieval(),
                    parentTemplate.queryUnderstanding(),
                    parentTemplate.tools(),
                    parentTemplate.memory(),
                    parentTemplate.judges(),
                    parentTemplate.mainOrComplement(),
                    integratedSingleTurnObjective(preset, parentTemplate),
                    parentTemplate.datasetPolicy());
        }
        return new RagPresetDefinition(
                preset,
                "UNSPECIFIED",
                preset.name(),
                "",
                "",
                "",
                "",
                "",
                "COMPLEMENT",
                integratedSingleTurnObjective(preset, null),
                "");
    }

    private static String integratedSingleTurnObjective(
            RagExperimentalPresetCode preset, RagPresetDefinition parentTemplate) {
        if (parentTemplate != null
                && parentTemplate.objective() != null
                && !parentTemplate.objective().isBlank()) {
            return parentTemplate.objective() + " (integrated single-turn composition)";
        }
        return preset.name() + " integrated single-turn composition";
    }
}
