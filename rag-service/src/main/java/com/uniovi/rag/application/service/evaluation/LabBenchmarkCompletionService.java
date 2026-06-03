package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Applies evidence policy before marking Lab benchmark async tasks as succeeded. */
@Service
public class LabBenchmarkCompletionService {

    private final LabBenchmarkEvidenceValidator evidenceValidator;

    public LabBenchmarkCompletionService(LabBenchmarkEvidenceValidator evidenceValidator) {
        this.evidenceValidator = evidenceValidator;
    }

    public void completeRun(AsyncTaskMutationService mutation, UUID taskId, UUID evaluationRunId, Map<String, Object> payload) {
        var failure = evidenceValidator.validateRun(evaluationRunId);
        if (failure.isPresent()) {
            mutation.markFailed(taskId, failure.get().message(), failure.get().failureCode());
            return;
        }
        Map<String, Object> enriched = new LinkedHashMap<>(payload != null ? payload : Map.of());
        enriched.put("benchmarkClosure", evidenceValidator.closureForRun(evaluationRunId));
        mutation.markSucceeded(taskId, Map.copyOf(enriched));
    }

    public void completeCampaign(
            AsyncTaskMutationService mutation, UUID taskId, List<UUID> runIds, Map<String, Object> payload) {
        var failure = evidenceValidator.validateCampaignRuns(runIds);
        if (failure.isPresent()) {
            mutation.markFailed(taskId, failure.get().message(), failure.get().failureCode());
            return;
        }
        Map<String, Object> enriched = new LinkedHashMap<>(payload != null ? payload : Map.of());
        enriched.put("benchmarkClosure", evidenceValidator.closureForCampaignRuns(runIds));
        mutation.markSucceeded(taskId, Map.copyOf(enriched));
    }
}
