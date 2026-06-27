package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionContextFactoryMemoryTraceTest {

    @Test
    void memoryAppliedForTrace_trueWhenDeterministicFollowUpApplied() {
        ConversationMemoryExecutionResult mem =
                new ConversationMemoryExecutionResult(
                        ConversationMemoryOutcome.MEMORY_APPLIED,
                        Optional.empty(),
                        false,
                        false,
                        false,
                        "¿Cuántos participantes asistieron a la reunión del 25/02/2026?",
                        List.of());

        assertThat(ExecutionContextFactory.memoryAppliedForTrace(mem)).isTrue();
    }

    @Test
    void memoryAppliedForTrace_falseWhenMemoryDisabled() {
        ConversationMemoryExecutionResult mem =
                new ConversationMemoryExecutionResult(
                        ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                        Optional.empty(),
                        false,
                        false,
                        false,
                        "hello",
                        List.of());

        assertThat(ExecutionContextFactory.memoryAppliedForTrace(mem)).isFalse();
    }
}
