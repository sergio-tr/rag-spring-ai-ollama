package com.uniovi.rag.application.service.evaluation.metrics;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkExportSupportTest {

    @Test
    void legacyP11_rowsMarkedUnsupportedAtExport() {
        assertThat(BenchmarkExportSupport.resolveBenchmarkSupportStatus("P11", Map.of()))
                .isEqualTo("SINGLE_TURN_UNSUPPORTED");
        assertThat(BenchmarkExportSupport.resolveBenchmarkSupportStatus("P12", Map.of()))
                .isEqualTo("SINGLE_TURN_UNSUPPORTED");
    }

    @Test
    void presetLabel_prefersCatalogOverModelIdLikeStoredLabel() {
        assertThat(BenchmarkExportSupport.sanitizePresetLabel("P0", "gemma3:4b", "Corpus text only"))
                .isEqualTo("Corpus text only");
    }

    @Test
    void presetOrder_usesProtocolStageIndexWhenPresent() {
        assertThat(BenchmarkExportSupport.resolvePresetOrder("P8", Map.of("protocolStageIndex", 8))).isEqualTo("8");
        assertThat(BenchmarkExportSupport.resolvePresetOrder("P3", Map.of())).isEqualTo("3");
    }
}
