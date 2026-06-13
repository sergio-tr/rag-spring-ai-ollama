package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperimentalPresetCanonicalCatalogDeterministicToolTest {

    @Test
    void p7_enablesDeterministicToolRouting_disablesFunctionCalling() {
        Map<String, Object> p7 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P7);
        assertThat(p7.get("deterministicToolRoutingEnabled")).isEqualTo(true);
        assertThat(p7.get("functionCallingEnabled")).isEqualTo(false);
        assertThat(p7.get("clarificationEnabled")).isEqualTo(false);
        assertThat(p7.get("memoryEnabled")).isEqualTo(false);
        assertThat(p7.get("adaptiveRoutingEnabled")).isEqualTo(false);
        assertThat(p7.get("toolsEnabled")).isEqualTo(true);
        assertThat(p7.get("metadataEnabled")).isEqualTo(true);
    }

    @Test
    void p6_doesNotEnableDeterministicToolRouting() {
        Map<String, Object> p6 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P6);
        assertThat(p6.get("deterministicToolRoutingEnabled")).isNull();
        assertThat(ExperimentalPresetCanonicalCatalog.singleTurnBenchmarkSelectable(RagExperimentalPresetCode.P7))
                .isTrue();
    }

    @Test
    void p4_doesNotEnableDeterministicToolRouting() {
        Map<String, Object> p4 = ExperimentalPresetCanonicalCatalog.effectiveRuntimeValues(RagExperimentalPresetCode.P4);
        assertThat(p4.get("deterministicToolRoutingEnabled")).isNull();
        assertThat(p4.get("toolsEnabled")).isEqualTo(true);
    }
}
