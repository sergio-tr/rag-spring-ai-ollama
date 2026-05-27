package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.LabJobEventType;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.service.async.AsyncTaskMutationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Runs every child {@link EvaluationRunEntity} of a campaign sequentially on one coordinator task.
 */
@Service
public class LabCampaignBenchmarkExecutor {

    /** Executes one evaluation run slice (same coordinator {@code async_task}). */
    @FunctionalInterface
    public interface CampaignRunSlice {
        Map<String, Object> run(AsyncTaskEntity task, AsyncTaskMutationService mutation, UUID evaluationRunId);
    }

    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationCampaignRepository evaluationCampaignRepository;
    private final LabJobEventService labJobEventService;

    public LabCampaignBenchmarkExecutor(
            EvaluationRunRepository evaluationRunRepository,
            EvaluationCampaignRepository evaluationCampaignRepository,
            LabJobEventService labJobEventService) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationCampaignRepository = evaluationCampaignRepository;
        this.labJobEventService = labJobEventService;
    }

    public void runCampaign(
            AsyncTaskEntity task,
            AsyncTaskMutationService mutation,
            UUID campaignId,
            CampaignRunSlice slice) {
        List<EvaluationRunEntity> runs =
                evaluationRunRepository.findByCampaign_IdOrderByCreatedAtAsc(campaignId);
        if (runs.isEmpty()) {
            throw new IllegalStateException("Campaign has no evaluation runs: " + campaignId);
        }
        CampaignExecutionPlan plan = buildPlan(campaignId, runs);
        UUID taskId = task.getId();
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.CAMPAIGN_ACCEPTED,
                "Campaign accepted",
                Map.of(
                        "campaignId", campaignId.toString(),
                        "totalItems", plan.totalItems(),
                        "axisCount", plan.axes().size()),
                campaignId,
                null,
                null,
                0,
                plan.totalItems(),
                null,
                null,
                null,
                null));
        List<Map<String, Object>> groups = new ArrayList<>();
        for (CampaignExecutionPlan.CampaignRunAxis axis : plan.axes()) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("runId", axis.runId().toString());
            g.put("axisLabel", axis.axisLabel());
            g.put("itemCount", axis.itemCount());
            groups.add(g);
        }
        labJobEventService.record(new LabJobEventRequest(
                taskId,
                LabJobEventType.CAMPAIGN_PLANNED,
                "Campaign planned",
                Map.of("campaignId", campaignId.toString(), "groups", groups, "totalItems", plan.totalItems()),
                campaignId,
                null,
                null,
                0,
                plan.totalItems(),
                null,
                null,
                null,
                null));

        Map<String, Object> lastPayload = Map.of("campaignId", campaignId.toString());
        for (EvaluationRunEntity run : runs) {
            lastPayload = slice.run(task, mutation, run.getId());
        }
        mutation.markSucceeded(task.getId(), lastPayload);
    }

    private CampaignExecutionPlan buildPlan(UUID campaignId, List<EvaluationRunEntity> runs) {
        int perAxisItemCount = 0;
        int plannedTotalItems = 0;
        EvaluationCampaignEntity camp = evaluationCampaignRepository.findById(campaignId).orElse(null);
        if (camp != null && camp.getMetaJson() != null) {
            Map<String, Object> meta = camp.getMetaJson();
            Object perAxis = meta.get("perAxisItemCount");
            Object total = meta.get("plannedTotalItems");
            if (perAxis instanceof Number n) {
                perAxisItemCount = Math.max(0, n.intValue());
            }
            if (total instanceof Number n) {
                plannedTotalItems = Math.max(0, n.intValue());
            }
        }
        List<CampaignExecutionPlan.CampaignRunAxis> axes = new ArrayList<>();
        for (EvaluationRunEntity run : runs) {
            String label =
                    run.getLlmModelId() != null
                            ? run.getLlmModelId()
                            : run.getEmbeddingModelId() != null
                                    ? run.getEmbeddingModelId()
                                    : resolvePresetAxisLabel(run);
            axes.add(new CampaignExecutionPlan.CampaignRunAxis(run.getId(), label, perAxisItemCount));
        }
        if (plannedTotalItems <= 0 && perAxisItemCount > 0) {
            plannedTotalItems = perAxisItemCount * runs.size();
        }
        if (plannedTotalItems <= 0) {
            plannedTotalItems = runs.size();
        }
        return new CampaignExecutionPlan(campaignId, List.copyOf(axes), plannedTotalItems);
    }

    private static String resolvePresetAxisLabel(EvaluationRunEntity run) {
        if (run.getAggregatesJson() != null) {
            Object codes = run.getAggregatesJson().get(BenchmarkRunOrchestrator.AGG_KEY_REQUESTED_PRESET_CODES);
            if (codes instanceof List<?> list && !list.isEmpty()) {
                Object first = list.getFirst();
                if (first != null) {
                    String code = String.valueOf(first).trim();
                    if (!code.isEmpty()) {
                        return code;
                    }
                }
            }
        }
        if (run.getPreset() != null
                && run.getPreset().getName() != null
                && !run.getPreset().getName().isBlank()) {
            return run.getPreset().getName().trim();
        }
        return run.getName();
    }
}
