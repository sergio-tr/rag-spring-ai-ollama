package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.interfaces.rest.dto.RuntimeConfigValidationIssueDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeIndexCompatibilityDto;
import com.uniovi.rag.interfaces.rest.dto.RuntimeSnapshotCapabilitiesDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PresetBaseFeatureSupportTest {

    @Test
    void chunkRagPreset_locksUseRetrievalOn() {
        Map<String, Object> p3 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3);

        assertThat(PresetBaseFeatureSupport.baseFeatures(p3)).contains("useRetrieval");
        assertThat(PresetBaseFeatureSupport.presetLockDisable("useRetrieval", p3))
                .isPresent()
                .get()
                .extracting("reasonCode")
                .isEqualTo(PresetBaseFeatureSupport.PRESET_BASE_FEATURE_LOCKED);
    }

    @Test
    void deterministicToolsPreset_locksToolsOn() {
        Map<String, Object> p7 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P7);

        assertThat(PresetBaseFeatureSupport.baseFeatures(p7)).contains("toolsEnabled", "useRetrieval");
    }

    @Test
    void rejectsDisablingBaseFeatureInPatch() {
        Map<String, Object> p3 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3);
        Map<String, Object> patch = Map.of("useRetrieval", false);
        Map<String, Object> merged = new LinkedHashMap<>(p3);
        merged.put("useRetrieval", false);

        List<RuntimeConfigValidationIssueDto> issues =
                PresetBaseFeatureSupport.validateRuntimeOverrideChange(p3, merged, patch, null);

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().code()).isEqualTo(PresetBaseFeatureSupport.PRESET_BASE_FEATURE_LOCKED);
        assertThat(issues.getFirst().field()).isEqualTo("useRetrieval");
    }

    @Test
    void rejectsEnablingDeferredOptionalFeature() {
        Map<String, Object> p3 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3);
        Map<String, Object> patch = Map.of("expansionEnabled", true);
        Map<String, Object> merged = new LinkedHashMap<>(p3);
        merged.put("expansionEnabled", true);

        List<RuntimeConfigValidationIssueDto> issues =
                PresetBaseFeatureSupport.validateRuntimeOverrideChange(p3, merged, patch, chunkIndex());

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().code()).isEqualTo(PresetBaseFeatureSupport.PRESET_FEATURE_TOGGLE_DEFERRED);
        assertThat(issues.getFirst().field()).isEqualTo("expansionEnabled");
    }

    @Test
    void structuredSearch_rejectsEnablingRetrievalStack() {
        Map<String, Object> p0 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P0);
        Map<String, Object> patch = Map.of("useRetrieval", true);
        Map<String, Object> merged = Map.of("useRetrieval", true);

        List<RuntimeConfigValidationIssueDto> issues =
                PresetBaseFeatureSupport.validateRuntimeOverrideChange(p0, merged, patch, structuredSearchIndex());

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().code()).isEqualTo(PresetBaseFeatureSupport.PROJECT_FEATURE_UNAVAILABLE);
        assertThat(issues.getFirst().field()).isEqualTo("useRetrieval");
    }

    @Test
    void allowsRetrievalNumericOverrides() {
        Map<String, Object> p3 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3);
        Map<String, Object> patch = Map.of("topK", 15, "similarityThreshold", 0.42);
        Map<String, Object> merged = new LinkedHashMap<>(p3);
        merged.putAll(patch);

        assertThat(PresetBaseFeatureSupport.validateRuntimeOverrideChange(p3, merged, patch, chunkIndex())).isEmpty();
    }

    @Test
    void throwIfInvalid_raisesRuntimeConfigurationInvalidException() {
        Map<String, Object> p7 =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P7);
        Map<String, Object> patch = Map.of("toolsEnabled", false);
        Map<String, Object> merged = new LinkedHashMap<>(p7);
        merged.put("toolsEnabled", false);

        assertThatThrownBy(
                        () ->
                                PresetBaseFeatureSupport.throwIfInvalid(
                                        p7, merged, patch, chunkIndex()))
                .isInstanceOf(RuntimeConfigurationInvalidException.class)
                .satisfies(
                        ex -> {
                            RuntimeConfigurationInvalidException rce = (RuntimeConfigurationInvalidException) ex;
                            assertThat(rce.code()).isEqualTo(PresetBaseFeatureSupport.PRESET_BASE_FEATURE_LOCKED);
                        });
    }

    private static RuntimeIndexCompatibilityDto chunkIndex() {
        return new RuntimeIndexCompatibilityDto(
                UUID.randomUUID(),
                null,
                "hash",
                Map.of("materializationStrategy", "CHUNK_LEVEL"),
                true,
                new RuntimeSnapshotCapabilitiesDto("CHUNK_LEVEL", false, "emb", 400, 40),
                null,
                true,
                "COMPATIBLE");
    }

    private static RuntimeIndexCompatibilityDto structuredSearchIndex() {
        return new RuntimeIndexCompatibilityDto(
                UUID.randomUUID(),
                null,
                "hash",
                Map.of("materializationStrategy", "STRUCTURED_SEARCH"),
                true,
                new RuntimeSnapshotCapabilitiesDto("STRUCTURED_SEARCH", false, "emb", 400, 40),
                null,
                true,
                "COMPATIBLE");
    }
}
