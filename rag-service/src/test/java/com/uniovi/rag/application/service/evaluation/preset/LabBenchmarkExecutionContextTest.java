package com.uniovi.rag.application.service.evaluation.preset;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabBenchmarkExecutionContextTest {

    @AfterEach
    void tearDown() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void open_sets_terminal_until_closed() throws Exception {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("useRetrieval", false);
        try (AutoCloseable ignored = LabBenchmarkExecutionContext.open(n)) {
            assertThat(LabBenchmarkExecutionContext.currentTerminalOverride()).contains(n);
        }
        assertThat(LabBenchmarkExecutionContext.currentTerminalOverride()).isEmpty();
    }

    @Test
    void open_null_is_noop_and_leaves_empty() throws Exception {
        try (AutoCloseable ignored = LabBenchmarkExecutionContext.open(null)) {
            assertThat(LabBenchmarkExecutionContext.currentTerminalOverride()).isEmpty();
        }
    }
}
