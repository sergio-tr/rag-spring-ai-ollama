package com.uniovi.rag.application.service.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountJobPayloadKeysTest {

    @Test
    void keys_areStable() {
        assertThat(AccountJobPayloadKeys.EXPORT_ARTIFACT_ID).isEqualTo("exportArtifactId");
        assertThat(AccountJobPayloadKeys.TASK_TYPE).isEqualTo("taskType");
    }
}
