package com.uniovi.rag.service.async.lab;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabEvalConcurrencyTest {

    @Test
    void serialEvalLock_isSharedSingleton() {
        assertThat(LabEvalConcurrency.SERIAL_EVAL).isNotNull();
        assertThat(LabEvalConcurrency.SERIAL_EVAL).isSameAs(LabEvalConcurrency.SERIAL_EVAL);
    }
}
