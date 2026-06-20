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

class ExperimentalPresetCanonicalCatalogIntegratedSingleTurnTest {

    @Test
    void p15_inheritsP9AndEnablesAdaptiveRouting() {
        Map<String, Object> p9 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P9);
        Map<String, Object> p15 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P15);

        assertThat(p15)
                .containsEntry("functionCallingEnabled", true)
                .containsEntry("functionCallingBackendProposalEnabled", true)
                .containsEntry("materializationStrategy", "HYBRID")
                .containsEntry("rankerEnabled", true)
                .containsEntry("postRetrievalEnabled", true)
                .containsEntry("toolsEnabled", true)
                .containsEntry("deterministicToolRoutingEnabled", false)
                .containsEntry("useAdvisor", false)
                .containsEntry("judgeEnabled", false)
                .containsEntry("clarificationEnabled", false)
                .containsEntry("memoryEnabled", false)
                .containsEntry("adaptiveRoutingEnabled", true);

        assertThat(p9.get("functionCallingEnabled")).isEqualTo(p15.get("functionCallingEnabled"));
        assertThat(p9.get("materializationStrategy")).isEqualTo(p15.get("materializationStrategy"));
    }

    @Test
    void p15_isLabSelectable_andNotMultiTurn() {
        assertThat(ExperimentalPresetBenchmarkGate.blockReason(RagExperimentalPresetCode.P15)).isEmpty();
        assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(RagExperimentalPresetCode.P15))
                .isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresMultiTurn(RagExperimentalPresetCode.P15)).isFalse();
    }

    @Test
    void p15CatalogRuntimeValues_passCompatibilityValidation() {
        RagFeatureConfiguration base = new RagFeatureConfiguration();
        RagPresetExperimentalOverlay.Overlay overlay =
                RagPresetExperimentalOverlay.build(base, RagExperimentalPresetCode.P15);
        RagConfig cfg =
                RagConfig.applyJsonOverrides(
                        RagConfig.fromFeatureConfiguration(
                                overlay.features(), 12, 0.6, "llm", "emb", "classifier", "simple"),
                        overlay.terminalRuntimeJson());
        CompatibilityValidator validator =
                new CompatibilityValidator(new CompatibilityRulesConfiguration().compatibilityRules());
        assertThat(validator.validate(CapabilitySet.fromRagConfig(cfg), cfg).valid()).isTrue();
    }
}
