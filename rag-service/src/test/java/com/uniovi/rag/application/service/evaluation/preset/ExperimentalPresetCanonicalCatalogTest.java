package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.config.CompatibilityValidator;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.config.CompatibilityRulesConfiguration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExperimentalPresetCanonicalCatalogTest {

    @Test
    void ids_areStable_and_allCodesAreCovered() {
        Set<RagExperimentalPresetCode> codes = Set.of(RagExperimentalPresetCode.values());
        assertThat(codes).hasSize(16);
        for (RagExperimentalPresetCode c : codes) {
            assertThat(ExperimentalPresetCanonicalCatalog.productPresetId(c)).isNotNull();
        }
    }

    @Test
    void accumulation_monotonic_fromP4_onwards() {
        // Minimal safety: verify required cumulative flags never regress after they become true.
        assertMonotonicTrue("useRetrieval", RagExperimentalPresetCode.P2, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("metadataEnabled", RagExperimentalPresetCode.P4, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("expansionEnabled", RagExperimentalPresetCode.P5, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("reasoningEnabled", RagExperimentalPresetCode.P6, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("toolsEnabled", RagExperimentalPresetCode.P9, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("rankerEnabled", RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("postRetrievalEnabled", RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("functionCallingEnabled", RagExperimentalPresetCode.P9, RagExperimentalPresetCode.P9);
        assertMonotonicTrue("useAdvisor", RagExperimentalPresetCode.P10, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("adaptiveRoutingEnabled", RagExperimentalPresetCode.P11, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("judgeEnabled", RagExperimentalPresetCode.P12, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("clarificationEnabled", RagExperimentalPresetCode.P13, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("memoryEnabled", RagExperimentalPresetCode.P14, RagExperimentalPresetCode.P14);
    }

    @Test
    void multiturn_onlyP13P14() {
        for (RagExperimentalPresetCode c : RagExperimentalPresetCode.values()) {
            boolean mt = ExperimentalPresetCanonicalCatalog.requiresMultiTurn(c);
            if (c == RagExperimentalPresetCode.P13 || c == RagExperimentalPresetCode.P14) {
                assertThat(mt).isTrue();
            } else {
                assertThat(mt).isFalse();
            }
        }
    }

    @Test
    void singleTurnLadder_p0ThroughP10AndP15_comparable_p11P14_notSelectableInLab() {
        for (RagExperimentalPresetCode c : RagExperimentalPresetCode.values()) {
            if (c.ordinal() <= RagExperimentalPresetCode.P10.ordinal() || c == RagExperimentalPresetCode.P15) {
                assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(c))
                        .as("%s should be a single-turn Lab preset", c)
                        .isTrue();
                assertThat(ExperimentalPresetCanonicalCatalog.singleTurnComparableMetric(c)).isTrue();
                assertThat(ExperimentalPresetCanonicalCatalog.requiresMultiTurn(c)).isFalse();
            } else if (c == RagExperimentalPresetCode.P11 || c == RagExperimentalPresetCode.P12) {
                assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(c)).isFalse();
                assertThat(ExperimentalPresetCanonicalCatalog.singleTurnComparableMetric(c)).isFalse();
                assertThat(ExperimentalPresetCanonicalCatalog.requiresMultiTurn(c)).isFalse();
            } else {
                assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(c))
                        .as("%s should not be compared as a single-turn Lab metric", c)
                        .isFalse();
                assertThat(ExperimentalPresetCanonicalCatalog.requiresMultiTurn(c)).isTrue();
            }
        }
    }

    @Test
    void keyRuntimeConfigs_matchDefensiblePresetLadder() {
        Map<String, Object> p0 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P0);
        assertThat(p0)
                .containsEntry("useRetrieval", false)
                .containsEntry("naiveFullCorpusInPromptEnabled", false)
                .containsEntry("corpusGroundedDirectWorkflow", false)
                .containsEntry("materializationStrategy", "CHUNK_LEVEL");

        Map<String, Object> p1 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P1);
        assertThat(p1)
                .containsEntry("useRetrieval", false)
                .containsEntry("naiveFullCorpusInPromptEnabled", true)
                .containsEntry("corpusGroundedDirectWorkflow", false);

        Map<String, Object> p2 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P2);
        assertThat(p2).containsEntry("useRetrieval", true).containsEntry("materializationStrategy", "DOCUMENT_LEVEL");

        Map<String, Object> p4 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P4);
        assertThat(p4)
                .containsEntry("materializationStrategy", "CHUNK_LEVEL")
                .containsEntry("metadataEnabled", true)
                .containsEntry("toolsEnabled", true);

        Map<String, Object> p8 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P8);
        assertThat(p8)
                .containsEntry("materializationStrategy", "HYBRID")
                .containsEntry("rankerEnabled", true)
                .containsEntry("postRetrievalEnabled", true)
                .containsEntry("toolsEnabled", true)
                .containsEntry("deterministicToolRoutingEnabled", false)
                .containsEntry("topK", 12)
                .containsEntry("similarityThreshold", 0.6);

        Map<String, Object> p12 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P12);
        assertThat(p12)
                .containsEntry("functionCallingEnabled", false)
                .containsEntry("useAdvisor", true)
                .containsEntry("adaptiveRoutingEnabled", true)
                .containsEntry("judgeEnabled", true)
                .containsEntry("clarificationEnabled", false)
                .containsEntry("memoryEnabled", false);

        Map<String, Object> p11 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P11);
        assertThat(p11)
                .containsEntry("adaptiveRoutingEnabled", true)
                .containsEntry("judgeEnabled", false)
                .containsEntry("clarificationEnabled", false)
                .containsEntry("memoryEnabled", false);

        Map<String, Object> p13 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P13);
        Map<String, Object> p14 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P14);
        assertThat(p13).containsEntry("clarificationEnabled", true).containsEntry("memoryEnabled", false);
        assertThat(p14).containsEntry("clarificationEnabled", true).containsEntry("memoryEnabled", true);
    }

    @Test
    void p4CatalogRuntimeValues_passMetadataRequiresToolsCompatibility() {
        RagFeatureConfiguration base = new RagFeatureConfiguration();
        RagPresetExperimentalOverlay.Overlay overlay =
                RagPresetExperimentalOverlay.build(base, RagExperimentalPresetCode.P4);
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
    void p0_labIndexRequirements_areNone_p2_requiresDocumentLevel() {
        var p0 = ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P0);
        var p1 = ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P1);
        var p2 = ExperimentalPresetCanonicalCatalog.effectiveIndexRequirements(RagExperimentalPresetCode.P2);
        assertThat(p0.requiredMaterialization())
                .isEqualTo(ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE);
        assertThat(p1.requiredMaterialization())
                .isEqualTo(ExperimentalPresetCanonicalCatalog.RequiredMaterialization.NONE);
        assertThat(p2.requiredMaterialization())
                .isEqualTo(ExperimentalPresetCanonicalCatalog.RequiredMaterialization.DOCUMENT_LEVEL);
        assertThat(ExperimentalPresetCanonicalCatalog.requiresSnapshotAssembledCorpusEvidence(
                        RagExperimentalPresetCode.P0))
                .isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresSnapshotForExecution(RagExperimentalPresetCode.P0))
                .isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.corpusRequired(RagExperimentalPresetCode.P0)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.canRunWithoutCorpus(RagExperimentalPresetCode.P0)).isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.needsVectorIndex(RagExperimentalPresetCode.P0)).isFalse();
        assertThat(ExperimentalPresetCanonicalCatalog.requiresSnapshotAssembledCorpusEvidence(
                        RagExperimentalPresetCode.P1))
                .isTrue();
        assertThat(ExperimentalPresetCanonicalCatalog.needsVectorIndex(RagExperimentalPresetCode.P1)).isFalse();
    }

    @Test
    void protocolLadderMarkdown_containsP0ThroughP15OnceEach() {
        String md = ExperimentalPresetCanonicalCatalog.protocolLadderMarkdownTable();
        for (RagExperimentalPresetCode c : RagExperimentalPresetCode.values()) {
            assertThat(md).contains("| " + c.name() + " |");
        }
        assertThat(md.lines().filter(l -> l.startsWith("| P")).count()).isEqualTo(16);
    }

    private static void assertMonotonicTrue(
            String key, RagExperimentalPresetCode start, RagExperimentalPresetCode end) {
        boolean reachedTrue = false;
        for (RagExperimentalPresetCode c : RagExperimentalPresetCode.values()) {
            if (c.ordinal() < start.ordinal() || c.ordinal() > end.ordinal()) {
                continue;
            }
            Map<String, Object> m = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(c);
            boolean v = m.get(key) instanceof Boolean b && b;
            if (!reachedTrue) {
                reachedTrue = v;
            } else {
                assertThat(v).as("Expected %s to remain true at %s", key, c.name()).isTrue();
            }
        }
    }
}

