package com.uniovi.rag.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PresetDraftCompatibilityResultTest {

    @Test
    void unavailable_returnsBlockingRuntimeStateIssue() {
        PresetDraftCompatibilityResult result = PresetDraftCompatibilityResult.unavailable();

        assertThat(result.compatibleWithActiveIndex()).isFalse();
        assertThat(result.indexRequirements()).isNull();
        assertThat(result.compatibilityStatus()).isNull();
        assertThat(result.blockingIssues()).hasSize(1);
        assertThat(result.blockingIssues().getFirst().code()).isEqualTo("RUNTIME_STATE_UNAVAILABLE");
    }
}
