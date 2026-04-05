package com.uniovi.rag.service.async.lab;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabJobPayloadKeysTest {

    @Test
    void keys_areStableForPayloadContracts() {
        assertThat(LabJobPayloadKeys.OLLAMA_MODEL).isEqualTo("model");
        assertThat(LabJobPayloadKeys.EVALUATION_RUN_ID).isEqualTo("evaluationRunId");
        assertThat(LabJobPayloadKeys.TRAIN_PATH).isEqualTo("trainPath");
    }
}
