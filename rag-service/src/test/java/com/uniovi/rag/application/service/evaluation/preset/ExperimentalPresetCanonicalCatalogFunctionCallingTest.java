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

class ExperimentalPresetCanonicalCatalogFunctionCallingTest {

    @Test
    void p9_enablesFunctionCallingWithDeterministicRoutingDisabled() {
        Map<String, Object> p9 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P9);

        assertThat(p9)
                .containsEntry("functionCallingEnabled", true)
                .containsEntry("deterministicToolRoutingEnabled", false)
                .containsEntry("useAdvisor", false)
                .containsEntry("clarificationEnabled", false)
                .containsEntry("memoryEnabled", false)
                .containsEntry("adaptiveRoutingEnabled", false);
    }

    @Test
    void p7_andP8_keepFunctionCallingDisabled() {
        Map<String, Object> p7 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P7);
        Map<String, Object> p8 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P8);

        assertThat(p7).containsEntry("functionCallingEnabled", false).containsEntry("deterministicToolRoutingEnabled", true);
        assertThat(p8).containsEntry("functionCallingEnabled", false).containsEntry("deterministicToolRoutingEnabled", false);
    }

    @Test
    void p9CatalogRuntimeValues_passCompatibilityValidation() {
        RagFeatureConfiguration base = new RagFeatureConfiguration();
        RagPresetExperimentalOverlay.Overlay overlay =
                RagPresetExperimentalOverlay.build(base, RagExperimentalPresetCode.P9);
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
