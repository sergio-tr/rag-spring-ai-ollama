package com.uniovi.rag.application.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.config.CompatibilityValidator;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.config.CompatibilityRulesConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatProductPresetAlignmentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void toolsOffBeforeDeterministicTools_on() {
        for (RagExperimentalPresetCode code :
                new RagExperimentalPresetCode[] {
                    RagExperimentalPresetCode.P4,
                    RagExperimentalPresetCode.P5,
                    RagExperimentalPresetCode.P6
                }) {
            assertThat(ChatProductPresetAlignment.toolsEnabledByDefault(code))
                    .as("%s should not enable tools by default", code)
                    .isFalse();
        }
    }

    @Test
    void toolsOnFromDeterministicTools_upward() {
        for (RagExperimentalPresetCode code :
                new RagExperimentalPresetCode[] {
                    RagExperimentalPresetCode.P7,
                    RagExperimentalPresetCode.P8,
                    RagExperimentalPresetCode.P9,
                    RagExperimentalPresetCode.P10,
                    RagExperimentalPresetCode.P15
                }) {
            assertThat(ChatProductPresetAlignment.toolsEnabledByDefault(code))
                    .as("%s should enable tools by default", code)
                    .isTrue();
        }
    }

    @Test
    void reasoningOffBeforeAdvancedPresets() {
        for (RagExperimentalPresetCode code :
                new RagExperimentalPresetCode[] {
                    RagExperimentalPresetCode.P4,
                    RagExperimentalPresetCode.P7,
                    RagExperimentalPresetCode.P8,
                    RagExperimentalPresetCode.P9,
                    RagExperimentalPresetCode.P15
                }) {
            assertThat(ChatProductPresetAlignment.reasoningEnabledByDefault(code))
                    .as("%s should not enable reasoning by default", code)
                    .isFalse();
        }
    }

    @Test
    void reasoningOnInAdvancedPresets() {
        for (RagExperimentalPresetCode code :
                new RagExperimentalPresetCode[] {
                    RagExperimentalPresetCode.P10,
                    RagExperimentalPresetCode.P11,
                    RagExperimentalPresetCode.P12
                }) {
            assertThat(ChatProductPresetAlignment.reasoningEnabledByDefault(code))
                    .as("%s should enable reasoning by default", code)
                    .isTrue();
        }
    }

    @Test
    void metadataRag_doesNotEnableToolsByDefault() {
        Map<String, Object> p4 =
                ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P4);
        assertThat(p4)
                .containsEntry("metadataEnabled", true)
                .containsEntry("toolsEnabled", false)
                .containsEntry("useRetrieval", true);
    }

    @Test
    void p4ProductValues_passCompatibilityWithMetadataWithoutTools() throws Exception {
        Map<String, Object> p4 =
                ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P4);
        RagConfig cfg =
                RagConfig.applyJsonOverrides(
                        baselineRag(),
                        MAPPER.valueToTree(p4));
        CompatibilityValidator validator =
                new CompatibilityValidator(new CompatibilityRulesConfiguration().compatibilityRules());
        CompatibilityResult result = validator.validate(CapabilitySet.fromRagConfig(cfg), cfg);
        assertThat(result.valid()).isTrue();
        assertThat(result.warnings().stream().anyMatch(w -> "METADATA_WITHOUT_TOOLS".equals(w.code()))).isTrue();
    }

    @Test
    void demoBest_matchesProductDescriptionAndValidatedFeatures() {
        Map<String, Object> demo = ChatProductPresetAlignment.demoBestProductValues();
        assertThat(demo)
                .containsEntry("useRetrieval", true)
                .containsEntry("metadataEnabled", true)
                .containsEntry("toolsEnabled", true)
                .containsEntry("functionCallingEnabled", true)
                .containsEntry("useAdvisor", true)
                .containsEntry("postRetrievalEnabled", true)
                .containsEntry("clarificationEnabled", true)
                .containsEntry("deterministicToolRoutingEnabled", true)
                .containsEntry("reasoningEnabled", false)
                .containsEntry("rankerEnabled", false)
                .containsEntry("judgeEnabled", false)
                .containsEntry("memoryEnabled", false)
                .containsEntry("similarityThreshold", ChatProductPresetAlignment.PRODUCT_SIMILARITY_THRESHOLD);

        String description = ChatProductPresetAlignment.DEMO_BEST_PRODUCT_DESCRIPTION;
        assertThat(description).contains("hybrid retrieval");
        assertThat(description).contains("reasoning");
        assertThat(description.toLowerCase()).contains("off");
        assertThat(demo.get("reasoningEnabled")).isEqualTo(false);
        assertThat(demo.get("rankerEnabled")).isEqualTo(false);
    }

    @Test
    void functionCallingOnlyAtFcPresetOrAbove() {
        Map<String, Object> p7 =
                ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P7);
        Map<String, Object> p9 =
                ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P9);
        Map<String, Object> p10 =
                ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P10);
        Map<String, Object> p15 =
                ChatProductPresetAlignment.effectiveProductRuntimeValues(RagExperimentalPresetCode.P15);

        assertThat(p7).containsEntry("functionCallingEnabled", false);
        assertThat(p9).containsEntry("functionCallingEnabled", true);
        assertThat(p10).containsEntry("functionCallingEnabled", false);
        assertThat(p15).containsEntry("functionCallingEnabled", true);
    }

    @Test
    void labCatalogRemainsUnchangedForP4Tools() {
        Map<String, Object> lab =
                ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P4);
        assertThat(lab).containsEntry("toolsEnabled", true);
    }

    private static RagConfig baselineRag() {
        RagFeatureConfiguration f = new RagFeatureConfiguration();
        f.setUseRetrieval(true);
        f.setToolsEnabled(false);
        f.setMetadataEnabled(false);
        return RagConfig.fromFeatureConfiguration(f, 10, 0.7, "llm", "emb", "classifier", "simple");
    }
}
