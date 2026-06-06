package com.uniovi.rag.application.service.evaluation.preset;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Map;

/**
 * Maps reference workbook presets P0–P14 onto (1) {@link RagFeatureConfiguration} for document loading / metadata services
 * and (2) terminal runtime JSON so {@link com.uniovi.rag.domain.runtime.RagConfig} merges include {@code naiveFullCorpusInPromptEnabled}, {@code topK}, etc.
 */
public final class RagPresetExperimentalOverlay {

    public record Overlay(RagFeatureConfiguration features, ObjectNode terminalRuntimeJson) {}

    private RagPresetExperimentalOverlay() {}

    public static Overlay build(RagFeatureConfiguration base, RagExperimentalPresetCode preset) {
        RagFeatureConfiguration f = RagFeatureConfigurationCopier.copy(base);
        // Canonical preset definition: parent+delta cumulative runtime map.
        ObjectNode json = ExperimentalPresetCanonicalCatalog.effectiveTerminalRuntimeJson(preset);
        applyFeatureFlagsFromCanonicalRuntimeMap(f, preset);
        return new Overlay(f, json);
    }

    private static void applyFeatureFlagsFromCanonicalRuntimeMap(
            RagFeatureConfiguration f, RagExperimentalPresetCode preset) {
        var m = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(preset);
        f.setUseRetrieval(bool(m, "useRetrieval"));
        f.setUseAdvisor(bool(m, "useAdvisor"));
        f.setMetadataEnabled(bool(m, "metadataEnabled"));
        f.setExpansionEnabled(bool(m, "expansionEnabled"));
        f.setNerEnabled(bool(m, "nerEnabled"));
        f.setToolsEnabled(bool(m, "toolsEnabled"));
        f.setFunctionCallingEnabled(bool(m, "functionCallingEnabled"));
        f.setReasoningEnabled(bool(m, "reasoningEnabled"));
        f.setRankerEnabled(bool(m, "rankerEnabled"));
        f.setPostRetrievalEnabled(bool(m, "postRetrievalEnabled"));
        f.setAdaptiveRoutingEnabled(bool(m, "adaptiveRoutingEnabled"));
        f.setJudgeEnabled(bool(m, "judgeEnabled"));
        f.setClarificationEnabled(bool(m, "clarificationEnabled"));
        f.setMemoryEnabled(bool(m, "memoryEnabled"));
    }

    private static boolean bool(Map<String, Object> m, String key) {
        Object v = m != null ? m.get(key) : null;
        return v instanceof Boolean b && b;
    }
}
