package com.uniovi.rag.application.service.runtime.routing.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.ExecutionOutcome;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IntegratedParentCampaignOutcomeResolverTest {

    @AfterEach
    void tearDown() {
        LabBenchmarkExecutionContext.clear();
    }

    @Test
    void tryResolve_replaysRecordedCampaignParentAnswer() {
        Runnable closeItem = LabBenchmarkExecutionContext.openBenchmarkItemScope("RAG-059");
        try {
            LabBenchmarkExecutionContext.recordCampaignParentOutcome(
                    "P7",
                    "RAG-059",
                    new CampaignParentOutcome(
                            "parent campaign answer",
                            "ChunkDenseRagWorkflow",
                            true,
                            "RETRIEVAL_WORKFLOW_ROUTE",
                            false,
                            false,
                            "none"));

            IntegratedParentCampaignOutcomeResolver resolver = new IntegratedParentCampaignOutcomeResolver();
            ExecutionOutcome outcome =
                    resolver.tryResolve(RagExperimentalPresetCode.P7, "RAG-059").orElseThrow();

            assertThat(outcome.result().answerText()).isEqualTo("parent campaign answer");
            assertThat(outcome.trace().routingRouteKind()).isEqualTo("RETRIEVAL_WORKFLOW_ROUTE");
        } finally {
            closeItem.run();
        }
    }
}
