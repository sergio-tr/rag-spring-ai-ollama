package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Applies evidence policy before marking Lab benchmark async tasks as succeeded. */
@Service
public class LabBenchmarkCompletionService {

    private final LabBenchmarkEvidenceValidator evidenceValidator;
    private final ObjectProvider<RuntimeObservability> runtimeObservability;

    public LabBenchmarkCompletionService(
            LabBenchmarkEvidenceValidator evidenceValidator,
            ObjectProvider<RuntimeObservability> runtimeObservability) {
        this.evidenceValidator = evidenceValidator;
        this.runtimeObservability = runtimeObservability;
    }

    public void completeRun(AsyncTaskMutationService mutation, UUID taskId, UUID evaluationRunId, Map<String, Object> payload) {
        var failure = evidenceValidator.validateRun(evaluationRunId);
        if (failure.isPresent()) {
            mutation.markFailed(taskId, failure.get().message(), failure.get().failureCode());
            return;
        }
        Map<String, Object> closure = evidenceValidator.closureForRun(evaluationRunId);
        Map<String, Object> enriched = new LinkedHashMap<>(payload != null ? payload : Map.of());
        enriched.put("benchmarkClosure", closure);
        mutation.markSucceeded(taskId, Map.copyOf(enriched));
        recordLabResultPersist(evaluationRunId, closure);
    }

    public void completeCampaign(
            AsyncTaskMutationService mutation, UUID taskId, List<UUID> runIds, Map<String, Object> payload) {
        var failure = evidenceValidator.validateCampaignRuns(runIds);
        if (failure.isPresent()) {
            mutation.markFailed(taskId, failure.get().message(), failure.get().failureCode());
            return;
        }
        Map<String, Object> closure = evidenceValidator.closureForCampaignRuns(runIds);
        Map<String, Object> enriched = new LinkedHashMap<>(payload != null ? payload : Map.of());
        enriched.put("benchmarkClosure", closure);
        mutation.markSucceeded(taskId, Map.copyOf(enriched));
        UUID primaryRunId = runIds != null && !runIds.isEmpty() ? runIds.getFirst() : null;
        recordLabResultPersist(primaryRunId, closure);
    }

    private void recordLabResultPersist(UUID runId, Map<String, Object> closure) {
        RuntimeObservability obs = runtimeObservability.getIfAvailable();
        if (obs == null || runId == null || closure == null || closure.isEmpty()) {
            return;
        }
        int itemCount = intValue(closure.get("totalRows"));
        int skippedCount = intValue(closure.get("skippedItems"));
        obs.labResultPersist(runId, itemCount, skippedCount);
    }

    private static int intValue(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
