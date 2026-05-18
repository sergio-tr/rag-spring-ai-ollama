package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpRollupCalculator;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.interfaces.rest.dto.StartCampaignRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LabCampaignService {

    private final EvaluationCampaignRepository evaluationCampaignRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationResultRepository evaluationResultRepository;

    public LabCampaignService(
            EvaluationCampaignRepository evaluationCampaignRepository,
            EvaluationRunRepository evaluationRunRepository,
            EvaluationResultRepository evaluationResultRepository) {
        this.evaluationCampaignRepository = evaluationCampaignRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationResultRepository = evaluationResultRepository;
    }

    /**
     * Starts a campaign by delegating to the canonical benchmark orchestrator.
     * <p>
     * Notes:
     * - LLM sweeps are supported and create multiple child runs.
     * - Embedding sweeps create one run per model when {@code embeddingModelIds} is set; align {@code indexSnapshotIds}
     *   for multi-model embedding campaigns.
     * - RAG preset sweeps are modeled as a single run that iterates preset codes (still exported per-preset).
     */
    @Transactional
    public Map<String, Object> startCampaign(
            UUID userId,
            BenchmarkKind kind,
            StartCampaignRequestDto req,
            BenchmarkRunOrchestrator orchestrator) {
        if (req == null || orchestrator == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        StartBenchmarkRunRequest body =
                new StartBenchmarkRunRequest(
                        req.datasetId(),
                        req.projectId(),
                        null,
                        req.name(),
                        null,
                        null,
                        null,
                        null,
                        req.experimentalPresetCodes(),
                        null,
                        null,
                        req.llmModelIds(),
                        req.embeddingModelIds(),
                        false,
                        req.name(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        req.indexSnapshotIds());

        BenchmarkJobAccepted accepted = orchestrator.startJsonBenchmark(userId, "USER", kind, body);
        UUID campaignId = accepted.campaignId().orElse(null);
        if (campaignId == null) {
            // Defensive: multi-model LLM starts always create a campaign; other kinds require explicit wiring.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Campaign was not created");
        }
        return Map.of(
                "campaignId", campaignId,
                "evaluationRunId", accepted.evaluationRunId(),
                "asyncTaskId", accepted.asyncTaskId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(UUID userId, UUID campaignId) {
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaignId", c.getId());
        out.put("studyType", c.getStudyType());
        out.put("name", c.getName());
        out.put("createdAt", c.getCreatedAt());
        out.put("projectId", c.getProject() != null ? c.getProject().getId() : null);
        out.put("runCount", runs.size());
        out.put("runIds", runs.stream().map(EvaluationRunEntity::getId).toList());
        out.put("meta", c.getMetaJson() != null ? c.getMetaJson() : Map.of());
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(UUID userId, UUID campaignId) {
        requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvaluationRunEntity r : runs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("runId", r.getId());
            row.put("name", r.getName());
            row.put("benchmarkKind", r.getBenchmarkKind());
            row.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            row.put("createdAt", r.getCreatedAt());
            row.put("completedAt", r.getCompletedAt());
            row.put("llmModelId", r.getLlmModelId());
            row.put("embeddingModelId", r.getEmbeddingModelId());
            out.add(row);
        }
        return out;
    }

    /**
     * Campaign MVP items (JSON) — concatenates all child-run items and preserves modelId/embeddingModelId per row.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportCampaignMvpItemsJson(UUID userId, UUID campaignId) {
        requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> rows = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            for (EvaluationResultEntity it : rows) {
                Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(it, run);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("campaignId", campaignId);
                row.put("runId", run.getId());
                row.put("evaluatedAt", it.getEvaluatedAt());
                row.put("mvp", mvp);
                items.add(row);
            }
        }
        return Map.of(
                "campaignId", campaignId,
                "exportedAt", Instant.now().toString(),
                "items", items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> campaignComparison(UUID userId, UUID campaignId) {
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> items = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            Map<String, Object> rollups = BenchmarkMvpRollupCalculator.build(items, run);
            // LLM sweep: each run is one model (simplest, stable)
            if ("LLM_MODEL_BASELINE".equalsIgnoreCase(c.getStudyType()) || "LLM_MODEL_SWEEP".equalsIgnoreCase(c.getStudyType())) {
                rows.add(rowFromRollupBucket("modelId", run.getLlmModelId(), rollupBucket(rollups, "globalMacro")));
                continue;
            }
            // RAG preset sweep: one run; break down by presetCode rollups
            if ("RAG_PRESET_SWEEP".equalsIgnoreCase(c.getStudyType()) || BenchmarkKind.RAG_PRESET_END_TO_END.name().equals(run.getBenchmarkKind())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> byPreset = (Map<String, Object>) rollups.getOrDefault("byPreset", Map.of());
                for (Map.Entry<String, Object> e : byPreset.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> bucket) {
                        //noinspection unchecked
                        rows.add(rowFromRollupBucket("presetCode", e.getKey(), (Map<String, Object>) bucket));
                    }
                }
                continue;
            }
            // Embedding sweep: currently unsupported at orchestrator level; if present, still export by embedding id.
            @SuppressWarnings("unchecked")
            Map<String, Object> byEmb = (Map<String, Object>) rollups.getOrDefault("byEmbeddingModel", Map.of());
            if (!byEmb.isEmpty()) {
                for (Map.Entry<String, Object> e : byEmb.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> bucket) {
                        //noinspection unchecked
                        rows.add(rowFromRollupBucket("embeddingModelId", e.getKey(), (Map<String, Object>) bucket));
                    }
                }
            } else {
                rows.add(rowFromRollupBucket("runId", run.getId().toString(), rollupBucket(rollups, "globalMacro")));
            }
        }
        return Map.of(
                "campaignId", campaignId,
                "studyType", c.getStudyType(),
                "rows", rows);
    }

    @Transactional(readOnly = true)
    public String exportCampaignItemsCsv(UUID userId, UUID campaignId) {
        requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<String> cols = new ArrayList<>();
        cols.add("campaignId");
        cols.add("runId");
        cols.addAll(LabEvaluationRunService.MVP_ITEMS_CSV_COLUMNS_FOR_TESTS());
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", cols)).append('\n');
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> items = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            for (EvaluationResultEntity it : items) {
                Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(it, run);
                List<String> cells = new ArrayList<>();
                cells.add(csvEscape(campaignId.toString()));
                cells.add(csvEscape(run.getId().toString()));
                for (String h : LabEvaluationRunService.MVP_ITEMS_CSV_COLUMNS_FOR_TESTS()) {
                    cells.add(csvEscape(row.getOrDefault(h, "")));
                }
                sb.append(String.join(",", cells)).append('\n');
            }
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportCampaignSummaryCsv(UUID userId, UUID campaignId) {
        Map<String, Object> cmp = campaignComparison(userId, campaignId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) cmp.getOrDefault("rows", List.of());
        List<String> cols = List.of(
                "campaignId",
                "groupKey",
                "groupValue",
                "totalItems",
                "executed",
                "notSupported",
                "failed",
                "meanExactMatch",
                "meanSemanticScore",
                "meanRecallAt1",
                "meanLatencyMs"
        );
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", cols)).append('\n');
        for (Map<String, Object> r : rows) {
            List<String> cells = new ArrayList<>();
            cells.add(csvEscape(String.valueOf(campaignId)));
            cells.add(csvEscape(String.valueOf(r.getOrDefault("groupKey", ""))));
            cells.add(csvEscape(String.valueOf(r.getOrDefault("groupValue", ""))));
            for (String k : cols.subList(3, cols.size())) {
                cells.add(csvEscape(String.valueOf(r.getOrDefault(k, ""))));
            }
            sb.append(String.join(",", cells)).append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportCampaignBundleJson(UUID userId, UUID campaignId) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("campaign", summary(userId, campaignId));
        bundle.put("comparison", campaignComparison(userId, campaignId));
        bundle.put("itemsBundle", exportCampaignMvpItemsJson(userId, campaignId));
        bundle.put("exportedAt", Instant.now().toString());
        return bundle;
    }

    private EvaluationCampaignEntity requireCampaign(UUID userId, UUID campaignId) {
        return evaluationCampaignRepository
                .findByIdAndUser_Id(campaignId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rollupBucket(Map<String, Object> rollups, String key) {
        Object o = rollups.get(key);
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static Map<String, Object> rowFromRollupBucket(String groupKey, String groupValue, Map<String, Object> bucket) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("groupKey", groupKey);
        out.put("groupValue", groupValue != null ? groupValue : "");
        @SuppressWarnings("unchecked")
        Map<String, Object> outcomeCounts = (Map<String, Object>) bucket.getOrDefault("outcomeCounts", Map.of());
        long executed = longNum(outcomeCounts.get(BenchmarkItemOutcome.EXECUTED.name()));
        long failed = longNum(outcomeCounts.get(BenchmarkItemOutcome.FAILED.name()));
        long notSupported = longNum(outcomeCounts.get(BenchmarkItemOutcome.NOT_SUPPORTED.name()));
        long total = outcomeCounts.values().stream().mapToLong(LabCampaignService::longNum).sum();
        out.put("totalItems", total);
        out.put("executed", executed);
        out.put("failed", failed);
        out.put("notSupported", notSupported);
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) bucket.getOrDefault("onExecuted", Map.of());
        out.put("meanExactMatch", onExecuted.get("meanNormalizedExactMatch"));
        out.put("meanSemanticScore", onExecuted.get("meanSemanticScoreWhereJudgePresent"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) bucket.getOrDefault("retrievalOnExecutedWhereApplicable", Map.of());
        out.put("meanRecallAt1", ret.get("meanRecallAt1"));
        out.put("meanLatencyMs", onExecuted.get("meanLatencyMsWherePresent"));
        return out;
    }

    private static long longNum(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        boolean needsQuotes = t.contains(",") || t.contains("\n") || t.contains("\r") || t.contains("\"");
        return needsQuotes ? "\"" + t + "\"" : t;
    }
}

