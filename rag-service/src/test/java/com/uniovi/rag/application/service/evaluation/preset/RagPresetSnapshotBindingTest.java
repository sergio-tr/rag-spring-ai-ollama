package com.uniovi.rag.application.service.evaluation.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RagPresetSnapshotBindingTest {

    @AfterEach
    void tearDown() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void openLab_registersCampaignPresetBindingForChunkGroup() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID chunkSnapshotId = UUID.fromString("3bc97dd6-908c-4828-b777-3f81cd3e312f");

        try (var ignored =
                LabBenchmarkExecutionContext.openLab(
                        JsonNodeFactory.instance.objectNode(),
                        runId,
                        projectId,
                        List.of(chunkSnapshotId),
                        LabPresetRunGroupKey.CHUNK_LEVEL.name(),
                        "P3",
                        true)) {
            assertThat(LabBenchmarkExecutionContext.currentLabRuntimeContext()).isPresent();
            assertThat(LabBenchmarkExecutionContext.currentLabRuntimeContext().orElseThrow().snapshotIds())
                    .containsExactly(chunkSnapshotId);
            assertThat(LabBenchmarkExecutionContext.campaignPresetBinding("P3")).isPresent();
            assertThat(LabBenchmarkExecutionContext.campaignPresetBinding("P3").orElseThrow().groupKey())
                    .isEqualTo(LabPresetRunGroupKey.CHUNK_LEVEL.name());
        }
    }
}
