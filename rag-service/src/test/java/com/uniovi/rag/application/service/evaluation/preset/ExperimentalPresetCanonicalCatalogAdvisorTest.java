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

class ExperimentalPresetCanonicalCatalogAdvisorTest {

    @Test
    void p10_enablesAdvisorWithFunctionCallingAndDeterministicRoutingDisabled() {
        Map<String, Object> p10 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P10);

        assertThat(p10)
                .containsEntry("useAdvisor", true)
                .containsEntry("functionCallingEnabled", false)
                .containsEntry("deterministicToolRoutingEnabled", false)
                .containsEntry("clarificationEnabled", false)
                .containsEntry("memoryEnabled", false)
                .containsEntry("adaptiveRoutingEnabled", false);
    }

    @Test
    void p8_andP9_keepAdvisorDisabled() {
        Map<String, Object> p8 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P8);
        Map<String, Object> p9 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P9);

        assertThat(p8).containsEntry("useAdvisor", false);
        assertThat(p9).containsEntry("useAdvisor", false).containsEntry("functionCallingEnabled", true);
    }

    @Test
    void p10CatalogRuntimeValues_passCompatibilityValidation() {
        RagFeatureConfiguration base = new RagFeatureConfiguration();
        RagPresetExperimentalOverlay.Overlay overlay =
                RagPresetExperimentalOverlay.build(base, RagExperimentalPresetCode.P10);
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
