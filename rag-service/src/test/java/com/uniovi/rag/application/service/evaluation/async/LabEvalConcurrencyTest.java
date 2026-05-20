package com.uniovi.rag.application.service.evaluation.async;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabEvalConcurrencyTest {

    @Test
    void serialEvalLock_isSharedSingleton() {
        assertThat(LabEvalConcurrency.SERIAL_EVAL).isNotNull();
        assertThat(LabEvalConcurrency.SERIAL_EVAL).isSameAs(LabEvalConcurrency.SERIAL_EVAL);
    }
}
