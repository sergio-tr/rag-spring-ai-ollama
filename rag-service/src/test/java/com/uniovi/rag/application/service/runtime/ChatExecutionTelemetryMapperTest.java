package com.uniovi.rag.application.service.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import org.junit.jupiter.api.Test;

class ChatExecutionTelemetryMapperTest {

    @Test
    void fromTrace_null_returnsEmptyMap() {
        assertThat(ChatExecutionTelemetryMapper.fromTrace(null)).isEmpty();
    }

    @Test
    void fromTrace_placeholder_hasSafeDefaults() {
        assertThat(ChatExecutionTelemetryMapper.fromTrace(ExecutionTrace.placeholder()))
                .containsEntry("clarificationRequired", false)
                .containsEntry("memoryAttempted", false)
                .containsEntry("routingAttempted", false)
                .containsEntry("judgeAttempted", false)
                .containsEntry("reasoningAttempted", false);
    }
}
