package com.uniovi.rag.service.async.lab;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LabJobPayloadsTest {

    @Test
    void evaluationRunId_returnsNull_whenPayloadNull() {
        assertThat(LabJobPayloads.evaluationRunId(null)).isNull();
    }

    @Test
    void evaluationRunId_returnsNull_whenKeyMissing() {
        assertThat(LabJobPayloads.evaluationRunId(Map.of("other", "x"))).isNull();
    }

    @Test
    void evaluationRunId_returnsNull_whenValueNotUuid() {
        assertThat(LabJobPayloads.evaluationRunId(Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, "not-a-uuid")))
                .isNull();
    }

    @Test
    void evaluationRunId_parsesUuidString() {
        UUID id = UUID.randomUUID();
        assertThat(LabJobPayloads.evaluationRunId(Map.of(LabJobPayloadKeys.EVALUATION_RUN_ID, id.toString())))
                .isEqualTo(id);
    }
}
