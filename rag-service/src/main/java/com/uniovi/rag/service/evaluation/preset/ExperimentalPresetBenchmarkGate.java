package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;

import java.util.Optional;

/**
 * Honest NOT_SUPPORTED reasons for presets that need a Lab harness beyond single-shot legacy HTTP evaluation.
 */
public final class ExperimentalPresetBenchmarkGate {

    private ExperimentalPresetBenchmarkGate() {}

    /** When non-empty, every catalog row for this preset must be recorded as NOT_SUPPORTED with this error_code. */
    public static Optional<String> blockReason(RagExperimentalPresetCode preset) {
        if (preset == null) {
            return Optional.of("PRESET_CODE_MISSING");
        }
        return switch (preset) {
            case P13 -> Optional.of("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
            case P14 -> Optional.of("PRESET_CONVERSATIONAL_MEMORY_BENCHMARK_NOT_SUPPORTED");
            default -> Optional.empty();
        };
    }
}
