package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentalPresetBenchmarkGateTest {

    @Test
    void p11_p12_blocked_honestly() {
        assertThat(ExperimentalPresetBenchmarkGate.blockReason(RagExperimentalPresetCode.P11))
                .contains("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        assertThat(ExperimentalPresetBenchmarkGate.blockReason(RagExperimentalPresetCode.P12))
                .contains("PRESET_CONVERSATIONAL_MEMORY_BENCHMARK_NOT_SUPPORTED");
    }

    @Test
    void p0_and_p9_not_blocked_by_gate() {
        assertThat(ExperimentalPresetBenchmarkGate.blockReason(RagExperimentalPresetCode.P0)).isEmpty();
        assertThat(ExperimentalPresetBenchmarkGate.blockReason(RagExperimentalPresetCode.P9)).isEmpty();
        assertThat(ExperimentalPresetBenchmarkGate.blockReason(RagExperimentalPresetCode.P14)).isEmpty();
    }
}
