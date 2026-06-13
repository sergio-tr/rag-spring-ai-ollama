package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.config.CompatibilityValidator;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.config.CompatibilityRulesConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperimentalPresetCanonicalCatalogAdvancedRetrievalTest {

    @Test
    void p8_enablesHybridRetrievalWithoutDeterministicToolRoute() {
        Map<String, Object> p8 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P8);

        assertThat(p8)
                .containsEntry("useRetrieval", true)
                .containsEntry("materializationStrategy", "HYBRID")
                .containsEntry("rankerEnabled", true)
                .containsEntry("postRetrievalEnabled", true)
                .containsEntry("toolsEnabled", true)
                .containsEntry("deterministicToolRoutingEnabled", false)
                .containsEntry("functionCallingEnabled", false)
                .containsEntry("useAdvisor", false)
                .containsEntry("clarificationEnabled", false)
                .containsEntry("memoryEnabled", false);
    }

    @Test
    void p8CatalogRuntimeValues_passMetadataRequiresToolsCompatibility() {
        RagFeatureConfiguration base = new RagFeatureConfiguration();
        RagPresetExperimentalOverlay.Overlay overlay =
                RagPresetExperimentalOverlay.build(base, RagExperimentalPresetCode.P8);
        RagConfig cfg =
                RagConfig.applyJsonOverrides(
                        RagConfig.fromFeatureConfiguration(
                                overlay.features(), 10, 0.7, "llm", "emb", "classifier", "simple"),
                        overlay.terminalRuntimeJson());
        CompatibilityValidator validator =
                new CompatibilityValidator(new CompatibilityRulesConfiguration().compatibilityRules());
        assertThat(validator.validate(CapabilitySet.fromRagConfig(cfg), cfg).valid()).isTrue();
    }

    @Test
    void p9_reEnablesToolsForFunctionCallingLane() {
        Map<String, Object> p9 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P9);

        assertThat(p9)
                .containsEntry("functionCallingEnabled", true)
                .containsEntry("toolsEnabled", true)
                .containsEntry("deterministicToolRoutingEnabled", false);
    }

    @Test
    void p3AndP4_remainDenseChunkRetrievalWithoutHybrid() {
        Map<String, Object> p3 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P3);
        Map<String, Object> p4 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P4);

        assertThat(p3).containsEntry("materializationStrategy", "CHUNK_LEVEL").doesNotContainEntry("materializationStrategy", "HYBRID");
        assertThat(p4).containsEntry("materializationStrategy", "CHUNK_LEVEL");
        assertThat(p4.get("rankerEnabled")).isNotEqualTo(true);
    }
}
