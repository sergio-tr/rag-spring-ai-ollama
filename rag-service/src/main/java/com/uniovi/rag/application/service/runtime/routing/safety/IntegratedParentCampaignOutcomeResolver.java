package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.preset.CampaignParentOutcome;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.runtime.RagExecutionOrchestrationSupport.ExecutionOutcome;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.engine.RagExecutionResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Replays campaign-recorded P6/P7 answers for integrated-route parent fallback. */
@Component
public class IntegratedParentCampaignOutcomeResolver {

    public Optional<ExecutionOutcome> tryResolve(
            RagExperimentalPresetCode parentPreset, String datasetQuestionId) {
        if (parentPreset == null || datasetQuestionId == null || datasetQuestionId.isBlank()) {
            return Optional.empty();
        }
        return LabBenchmarkExecutionContext.campaignParentOutcome(parentPreset.name(), datasetQuestionId)
                .map(IntegratedParentCampaignOutcomeMaterializer::toExecutionOutcome);
    }

    public Optional<String> currentDatasetQuestionId() {
        return LabBenchmarkExecutionContext.currentDatasetQuestionId();
    }

    static final class IntegratedParentCampaignOutcomeMaterializer {

        private IntegratedParentCampaignOutcomeMaterializer() {}

        static ExecutionOutcome toExecutionOutcome(CampaignParentOutcome outcome) {
            ExecutionTrace trace =
                    ExecutionTrace.campaignParentReplay(
                            outcome.workflowName(),
                            outcome.retrievalUsed(),
                            outcome.routingRouteKind(),
                            outcome.abstentionTriggered());
            RagExecutionResult result =
                    new RagExecutionResult(
                            outcome.answerText(),
                            outcome.workflowName(),
                            outcome.retrievalUsed(),
                            false,
                            Optional.empty(),
                            Optional.empty(),
                            List.of(),
                            trace,
                            outcome.toolUsedLabel(),
                            null,
                            outcome.usedTool(),
                            List.of(),
                            Optional.empty(),
                            List.of());
            return new ExecutionOutcome(result, trace);
        }
    }
}
