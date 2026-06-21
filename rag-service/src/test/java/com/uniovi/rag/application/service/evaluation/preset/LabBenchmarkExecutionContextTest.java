package com.uniovi.rag.application.service.evaluation.preset;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
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

    @Test
    void campaignParentOutcomes_survivePresetBoundaryClearWithinCampaign() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignParentOutcome outcome =
                new CampaignParentOutcome(
                        "parent answer",
                        "ChunkDenseRagWorkflow",
                        true,
                        "RETRIEVAL_WORKFLOW_ROUTE",
                        false,
                        false,
                        "none");

        try (AutoCloseable ignored = LabBenchmarkExecutionContext.openCampaignScope(campaignId)) {
            LabBenchmarkExecutionContext.recordCampaignParentOutcome("P7", "RAG-059", outcome);
            LabBenchmarkExecutionContext.clearCampaignPresetBindings();
            assertThat(LabBenchmarkExecutionContext.campaignParentOutcome("P7", "RAG-059"))
                    .contains(outcome);
            assertThat(CampaignParentOutcomeStore.snapshot(campaignId)).containsKey("P7");
        }

        assertThat(LabBenchmarkExecutionContext.campaignParentOutcome("P7", "RAG-059")).isEmpty();
        assertThat(CampaignParentOutcomeStore.snapshot(campaignId)).isEmpty();
    }

    @Test
    void campaignParentOutcomes_useThreadLocalWhenNoCampaignScope() {
        CampaignParentOutcome outcome =
                new CampaignParentOutcome(
                        "thread-local answer",
                        "deterministic-tool",
                        false,
                        "DETERMINISTIC_TOOL_ROUTE",
                        false,
                        true,
                        "filter_and_list");

        LabBenchmarkExecutionContext.recordCampaignParentOutcome("P6", "RAG-016", outcome);
        LabBenchmarkExecutionContext.clearCampaignPresetBindings();

        assertThat(LabBenchmarkExecutionContext.campaignParentOutcome("P6", "RAG-016")).contains(outcome);
    }
}
