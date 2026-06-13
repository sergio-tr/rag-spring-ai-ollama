package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the terminal async-task payload for multi-run Lab campaigns.
 * <p>
 * Authoritative item rows live in {@code evaluation_result}; this payload exposes child-run summaries and DB recovery paths.
 */
final class LabCampaignTerminalPayloadBuilder {

    static final String KEY_RESULTS_SOURCE = "resultsSource";
    static final String KEY_CHILD_RUNS = "childRuns";
    static final String KEY_PERSISTED_ITEM_COUNT = "persistedItemCount";
    static final String KEY_RECOVERY = "recovery";

    private LabCampaignTerminalPayloadBuilder() {}

    static Map<String, Object> build(
            UUID campaignId,
            List<EvaluationRunEntity> childRuns,
            EvaluationResultRepository evaluationResultRepository,
            LabPresetAxisSupport labPresetAxisSupport) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("campaignId", campaignId != null ? campaignId.toString() : null);
        out.put(KEY_RESULTS_SOURCE, "DATABASE");

        List<Map<String, Object>> children = new ArrayList<>();
        int totalPersisted = 0;
        for (EvaluationRunEntity run : childRuns) {
            if (run == null || run.getId() == null) {
                continue;
            }
            int itemCount = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId()).size();
            totalPersisted += itemCount;
            LinkedHashMap<String, Object> child = new LinkedHashMap<>();
            child.put("runId", run.getId().toString());
            child.put("presetKey", labPresetAxisSupport.resolvePresetCode(run));
            child.put("presetLabel", labPresetAxisSupport.resolvePresetLabel(run));
            child.put("comparisonLabel", labPresetAxisSupport.comparisonLabel(run));
            child.put("modelId", run.getLlmModelId());
            child.put("itemCount", itemCount);
            child.put(
                    "status",
                    run.getStatus() != null ? run.getStatus().name() : null);
            children.add(Collections.unmodifiableMap(new LinkedHashMap<>(child)));
        }
        out.put(KEY_CHILD_RUNS, List.copyOf(children));
        out.put(KEY_PERSISTED_ITEM_COUNT, totalPersisted);

        if (campaignId != null) {
            String base = "/lab/campaigns/" + campaignId;
            out.put(
                    KEY_RECOVERY,
                    Map.of(
                            "comparisonPath", base + "/comparison",
                            "exportItemsPath", base + "/export/campaign-items.json",
                            "runsPath", base + "/runs"));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(out));
    }
}
