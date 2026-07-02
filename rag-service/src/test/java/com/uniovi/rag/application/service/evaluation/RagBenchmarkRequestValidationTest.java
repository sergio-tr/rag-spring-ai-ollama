package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagBenchmarkRequestValidationTest {

    @Test
    void stockPhase52RequestPreservesAutoReindexFalseAndEmbeddingModel() {
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.fromString("00000000-0000-7000-8000-000000000001"),
                        UUID.fromString("50e06026-f3f4-40d3-9f18-e660a2db9651"),
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "phase52-rag-targeted",
                        null,
                        null,
                        null,
                        null,
                        List.of("P3", "P8", "P10"),
                        "gemma4:12b",
                        "bge-m3",
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.<UUID>of(),
                        List.of("RAG-001", "RAG-002"),
                        null,
                        null,
                        Map.of());

        assertThat(req.autoReindexEffective()).isFalse();
        assertThat(req.allowActiveSnapshotMutationEffective()).isFalse();
        assertThat(req.reuseCompatibleActiveSnapshot()).isTrue();
        assertThat(req.embeddingModelId()).isEqualTo("bge-m3");
        assertThat(req.experimentalPresetCodes()).containsExactly("P3", "P8", "P10");
        assertThat(req.hasDatasetQuestionSubset()).isTrue();
    }
}
