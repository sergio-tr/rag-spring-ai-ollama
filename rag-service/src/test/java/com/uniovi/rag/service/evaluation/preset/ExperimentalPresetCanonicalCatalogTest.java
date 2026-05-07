package com.uniovi.rag.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExperimentalPresetCanonicalCatalogTest {

    @Test
    void ids_areStable_and_allCodesAreCovered() {
        Set<RagExperimentalPresetCode> codes = Set.of(RagExperimentalPresetCode.values());
        assertThat(codes).hasSize(15);
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
        assertMonotonicTrue("toolsEnabled", RagExperimentalPresetCode.P7, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("rankerEnabled", RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("postRetrievalEnabled", RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P14);
        assertMonotonicTrue("functionCallingEnabled", RagExperimentalPresetCode.P9, RagExperimentalPresetCode.P14);
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

