package com.uniovi.rag.application.service.evaluation.preset;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignParentOutcomeStoreTest {

    @AfterEach
    void tearDown() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void p7OutcomeForItemIsVisibleToP15WithinSameCampaign() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignParentOutcome p7 =
                CampaignParentOutcome.capture(
                        "P7",
                        "RAG-059",
                        "p7 answer",
                        "deterministic-tool",
                        false,
                        "DETERMINISTIC_TOOL_ROUTE",
                        false,
                        true,
                        "filter_and_list",
                        "TOOL_FINAL",
                        "COMPLETE",
                        "SAFE",
                        "true",
                        List.of());

        try (AutoCloseable scope = LabBenchmarkExecutionContext.openCampaignScope(campaignId)) {
            LabBenchmarkExecutionContext.recordCampaignParentOutcome("P7", "RAG-059", p7);
            LabBenchmarkExecutionContext.clearCampaignPresetBindings();
            assertThat(LabBenchmarkExecutionContext.campaignParentOutcome("P7", "RAG-059")).contains(p7);
        }
    }

    @Test
    void outcomesAreIsolatedAcrossCampaigns() throws Exception {
        UUID campaignA = UUID.randomUUID();
        UUID campaignB = UUID.randomUUID();
        CampaignParentOutcome outcome =
                CampaignParentOutcome.capture(
                        "P7",
                        "RAG-059",
                        "campaign-a",
                        "wf",
                        false,
                        "ROUTE",
                        false,
                        false,
                        "none",
                        "GENERATED",
                        "",
                        "",
                        "",
                        List.of());

        try (AutoCloseable a = LabBenchmarkExecutionContext.openCampaignScope(campaignA)) {
            LabBenchmarkExecutionContext.recordCampaignParentOutcome("P7", "RAG-059", outcome);
            assertThat(LabBenchmarkExecutionContext.campaignParentOutcome("P7", "RAG-059")).contains(outcome);
        }
        try (AutoCloseable b = LabBenchmarkExecutionContext.openCampaignScope(campaignB)) {
            assertThat(LabBenchmarkExecutionContext.campaignParentOutcome("P7", "RAG-059")).isEmpty();
        }
    }

    @Test
    void outcomesAreIsolatedAcrossItems() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignParentOutcome p7a =
                CampaignParentOutcome.capture(
                        "P7",
                        "RAG-059",
                        "a",
                        "wf",
                        false,
                        "ROUTE",
                        false,
                        false,
                        "none",
                        "GENERATED",
                        "",
                        "",
                        "",
                        List.of());

        try (AutoCloseable scope = LabBenchmarkExecutionContext.openCampaignScope(campaignId)) {
            LabBenchmarkExecutionContext.recordCampaignParentOutcome("P7", "RAG-059", p7a);
            assertThat(LabBenchmarkExecutionContext.campaignParentOutcome("P7", "RAG-016")).isEmpty();
        }
    }

    @Test
    void registryClearedOnlyWhenCampaignScopeCloses() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignParentOutcome outcome =
                CampaignParentOutcome.capture(
                        "P6",
                        "RAG-016",
                        "p6",
                        "wf",
                        true,
                        "RETRIEVAL_WORKFLOW_ROUTE",
                        false,
                        false,
                        "none",
                        "GENERATED",
                        "",
                        "",
                        "",
                        List.of());

        AutoCloseable scope = LabBenchmarkExecutionContext.openCampaignScope(campaignId);
        LabBenchmarkExecutionContext.recordCampaignParentOutcome("P6", "RAG-016", outcome);
        LabBenchmarkExecutionContext.clearCampaignPresetBindings();
        assertThat(CampaignParentOutcomeStore.snapshot(campaignId)).isNotEmpty();
        scope.close();
        assertThat(CampaignParentOutcomeStore.snapshot(campaignId)).isEmpty();
    }
}
