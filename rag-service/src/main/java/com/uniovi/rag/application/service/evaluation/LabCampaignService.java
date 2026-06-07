package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.EvaluationStudyType;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpRollupCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.LabBenchmarkExportLabels;
import com.uniovi.rag.infrastructure.persistence.evaluation.LabCampaignHumanExportBuilder;
import com.uniovi.rag.interfaces.rest.dto.StartCampaignRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LabCampaignService {

    static final String COMPARISON_AXIS_LLM = "LLM_MODEL";
    static final String COMPARISON_AXIS_EMBEDDING = "EMBEDDING_MODEL";
    static final String COMPARISON_AXIS_PRESET = "PRESET_CODE";

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
     * LLM, embedding, and RAG preset sweeps fan out into one child run per comparison axis value.
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
                        req.corpusId(),
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
        CampaignContext ctx = resolveCampaignContext(c, runs);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaignId", c.getId());
        out.put("campaignType", ctx.campaignType());
        out.put("comparisonAxis", ctx.comparisonAxis());
        out.put("comparativeMode", ctx.comparativeMode());
        out.put("axisCount", ctx.axisCount());
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
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        CampaignContext ctx = resolveCampaignContext(c, evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId));
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvaluationRunEntity r : runs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("runId", r.getId());
            row.put("runName", r.getName());
            row.put("benchmarkKind", r.getBenchmarkKind());
            row.put("status", r.getStatus() != null ? r.getStatus().name() : null);
            row.put("createdAt", r.getCreatedAt());
            row.put("completedAt", r.getCompletedAt());
            row.put("llmModelId", r.getLlmModelId());
            row.put("embeddingModelId", r.getEmbeddingModelId());
            row.put("presetCode", resolvePresetCode(r));
            row.put("modelLabel", humanModelLabel(r));
            row.put("presetLabel", humanPresetLabel(r));
            row.put("corpusName", humanCorpusName(r));
            row.put("datasetName", humanDatasetName(r));
            row.put("comparisonAxis", ctx.comparisonAxis());
            row.put("axisValue", resolveAxisValue(ctx, r));
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportCampaignMvpItemsJson(UUID userId, UUID campaignId) {
        return exportCampaignItemsJson(userId, campaignId);
    }

    /** Human-readable campaign items export ({@code campaign-items.json} contract). */
    @Transactional(readOnly = true)
    public Map<String, Object> exportCampaignItemsJson(UUID userId, UUID campaignId) {
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        CampaignContext ctx = resolveCampaignContext(c, runs);
        Map<String, Object> out =
                new LinkedHashMap<>(
                        LabCampaignHumanExportBuilder.campaignHeader(
                                c, ctx.campaignType(), ctx.comparisonAxis(), ctx.comparativeMode(), runs));
        out.put("exportKind", LabCampaignHumanExportBuilder.EXPORT_KIND_ITEMS);
        out.put("exportedAt", Instant.now().toString());
        List<Map<String, Object>> items = new ArrayList<>();
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> rows =
                    evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            for (EvaluationResultEntity it : rows) {
                items.add(
                        LabCampaignHumanExportBuilder.buildHumanItemRow(
                                campaignId, ctx.campaignType(), ctx.comparisonAxis(), run, it));
            }
        }
        out.put("items", items);
        return out;
    }

    /** Human-readable campaign summary export ({@code campaign-summary.json} contract). */
    @Transactional(readOnly = true)
    public Map<String, Object> exportCampaignSummaryJson(UUID userId, UUID campaignId) {
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        CampaignContext ctx = resolveCampaignContext(c, runs);
        Map<String, Object> cmp = campaignComparison(userId, campaignId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> comparisonRows = (List<Map<String, Object>>) cmp.getOrDefault("rows", List.of());
        List<Map<String, Object>> humanRows = new ArrayList<>();
        for (Map<String, Object> row : comparisonRows) {
            UUID runId = parseUuid(row.get("runId"));
            EvaluationRunEntity run =
                    runs.stream().filter(r -> r.getId().equals(runId)).findFirst().orElse(runs.isEmpty() ? null : runs.getFirst());
            humanRows.add(
                    LabCampaignHumanExportBuilder.buildSummaryRow(
                            row, run, campaignId, ctx.campaignType(), ctx.comparisonAxis(), ctx.comparativeMode()));
        }
        Map<String, Object> out =
                new LinkedHashMap<>(
                        LabCampaignHumanExportBuilder.campaignHeader(
                                c, ctx.campaignType(), ctx.comparisonAxis(), ctx.comparativeMode(), runs));
        out.put("exportKind", LabCampaignHumanExportBuilder.EXPORT_KIND_SUMMARY);
        out.put("exportedAt", Instant.now().toString());
        out.put("rows", humanRows);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> campaignComparison(UUID userId, UUID campaignId) {
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        CampaignContext ctx = resolveCampaignContext(c, runs);
        List<Map<String, Object>> rows = buildComparisonRows(ctx, runs);
        long failedRuns = runs.stream().filter(r -> r.getStatus() != null && "ERROR".equals(r.getStatus().name())).count();
        long skippedRuns = runs.stream().filter(r -> r.getStatus() != null && "SKIPPED".equals(r.getStatus().name())).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaignId", campaignId);
        out.put("campaignType", ctx.campaignType());
        out.put("comparisonAxis", ctx.comparisonAxis());
        out.put("comparisonAxisLabel", LabBenchmarkExportLabels.comparisonAxisLabel(resolveBenchmarkKind(runs)));
        out.put("comparativeMode", ctx.comparativeMode());
        out.put("axisCount", ctx.axisCount());
        out.put("studyType", c.getStudyType());
        out.put("runs", runs.stream().map(this::runSummaryRow).toList());
        out.put("rows", rows);
        out.put("failedRuns", failedRuns);
        out.put("skippedRuns", skippedRuns);
        return out;
    }

    @Transactional(readOnly = true)
    public String exportCampaignItemsCsv(UUID userId, UUID campaignId) {
        EvaluationCampaignEntity c = requireCampaign(userId, campaignId);
        CampaignContext ctx = resolveCampaignContext(c, evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId));
        List<EvaluationRunEntity> runs = evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId);
        List<String> cols = new ArrayList<>();
        cols.add("campaignId");
        cols.add("campaign_type");
        cols.add("comparison_axis");
        cols.add("runId");
        cols.add("run_name");
        cols.add("model_label");
        cols.add("preset_label");
        cols.add("knowledge_base_id");
        cols.add("knowledge_base_name");
        cols.add("corpus_name");
        cols.add("dataset_name");
        cols.add("snapshot_id");
        cols.add("document_name");
        cols.addAll(LabEvaluationRunService.MVP_ITEMS_CSV_COLUMNS_FOR_TESTS());
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", cols)).append('\n');
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> items = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            for (EvaluationResultEntity it : items) {
                Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(it, run);
                List<String> cells = new ArrayList<>();
                cells.add(csvEscape(campaignId.toString()));
                cells.add(csvEscape(ctx.campaignType()));
                cells.add(csvEscape(ctx.comparisonAxis()));
                cells.add(csvEscape(run.getId().toString()));
                cells.add(csvEscape(nullToEmpty(run.getName())));
                cells.add(csvEscape(humanModelLabel(run)));
                cells.add(csvEscape(humanPresetLabel(run)));
                UUID kbId = knowledgeBaseId(run);
                cells.add(csvEscape(kbId != null ? kbId.toString() : ""));
                cells.add(csvEscape(humanCorpusName(run)));
                cells.add(csvEscape(humanCorpusName(run)));
                cells.add(csvEscape(humanDatasetName(run)));
                Map<String, Object> human =
                        LabCampaignHumanExportBuilder.buildHumanItemRow(
                                campaignId, ctx.campaignType(), ctx.comparisonAxis(), run, it);
                cells.add(csvEscape(String.valueOf(human.getOrDefault("snapshotId", ""))));
                cells.add(csvEscape(String.valueOf(human.getOrDefault("documentName", ""))));
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
                "campaign_type",
                "comparison_axis",
                "comparative_mode",
                "runId",
                "run_name",
                "model_label",
                "preset_label",
                "corpus_name",
                "dataset_name",
                "axis_value",
                "status",
                "failure_reason",
                "totalItems",
                "executed",
                "notSupported",
                "failed",
                "skipped",
                "meanExactMatch",
                "meanSemanticScore",
                "meanRecallAt1",
                "meanLatencyMs"
        );
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", cols)).append('\n');
        for (Map<String, Object> r : rows) {
            List<String> cells = new ArrayList<>();
            cells.add(csvEscape(String.valueOf(cmp.getOrDefault("campaignId", ""))));
            cells.add(csvEscape(String.valueOf(cmp.getOrDefault("campaignType", ""))));
            cells.add(csvEscape(String.valueOf(cmp.getOrDefault("comparisonAxis", ""))));
            cells.add(csvEscape(String.valueOf(cmp.getOrDefault("comparativeMode", ""))));
            for (String k : cols.subList(4, cols.size())) {
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
        bundle.put("itemsBundle", exportCampaignItemsJson(userId, campaignId));
        bundle.put("summaryJson", exportCampaignSummaryJson(userId, campaignId));
        bundle.put("exportedAt", Instant.now().toString());
        return bundle;
    }

    private List<Map<String, Object>> buildComparisonRows(CampaignContext ctx, List<EvaluationRunEntity> runs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> items = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            Map<String, Object> rollups = BenchmarkMvpRollupCalculator.build(items, run);
            String axisValue = resolveAxisValue(ctx, run);
            Map<String, Object> bucket = rollupBucket(rollups, "globalMacro");
            Map<String, Object> row = rowFromRollupBucket(ctx.comparisonAxis(), axisValue, bucket);
            row.put("runId", run.getId());
            row.put("runName", run.getName());
            row.put("modelLabel", humanModelLabel(run));
            row.put("presetLabel", humanPresetLabel(run));
            row.put("corpusName", humanCorpusName(run));
            row.put("datasetName", humanDatasetName(run));
            row.put("status", run.getStatus() != null ? run.getStatus().name() : "");
            row.put("failureReason", resolveFailureReason(run, bucket));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> runSummaryRow(EvaluationRunEntity run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", run.getId());
        m.put("runName", run.getName());
        m.put("status", run.getStatus() != null ? run.getStatus().name() : null);
        m.put("llmModelId", run.getLlmModelId());
        m.put("embeddingModelId", run.getEmbeddingModelId());
        m.put("presetCode", resolvePresetCode(run));
        m.put("modelLabel", humanModelLabel(run));
        m.put("presetLabel", humanPresetLabel(run));
        m.put("corpusName", humanCorpusName(run));
        m.put("datasetName", humanDatasetName(run));
        return m;
    }

    private static CampaignContext resolveCampaignContext(EvaluationCampaignEntity c, List<EvaluationRunEntity> runs) {
        String studyType = c.getStudyType() != null ? c.getStudyType().trim() : "";
        String campaignType;
        String comparisonAxis;
        if (EvaluationStudyType.LLM_MODEL_BASELINE.name().equalsIgnoreCase(studyType)
                || "LLM_MODEL_SWEEP".equalsIgnoreCase(studyType)) {
            campaignType = "LLM";
            comparisonAxis = COMPARISON_AXIS_LLM;
        } else if (EvaluationStudyType.EMBEDDING_MODEL_BASELINE.name().equalsIgnoreCase(studyType)
                || "EMBEDDING_MODEL_SWEEP".equalsIgnoreCase(studyType)) {
            campaignType = "EMBEDDING";
            comparisonAxis = COMPARISON_AXIS_EMBEDDING;
        } else {
            campaignType = "RAG_PRESET";
            comparisonAxis = COMPARISON_AXIS_PRESET;
        }
        int axisCount = countDistinctAxisValues(comparisonAxis, runs);
        boolean comparativeMode = readComparativeMode(c) || axisCount >= 2;
        return new CampaignContext(campaignType, comparisonAxis, comparativeMode, axisCount);
    }

    private static boolean readComparativeMode(EvaluationCampaignEntity c) {
        if (c.getMetaJson() == null) {
            return false;
        }
        Object v = c.getMetaJson().get("comparativeMode");
        return Boolean.TRUE.equals(v);
    }

    private static int countDistinctAxisValues(String comparisonAxis, List<EvaluationRunEntity> runs) {
        return (int) runs.stream().map(r -> resolveAxisValueStatic(comparisonAxis, r)).distinct().count();
    }

    private static String resolveAxisValue(CampaignContext ctx, EvaluationRunEntity run) {
        return resolveAxisValueStatic(ctx.comparisonAxis(), run);
    }

    private static String resolveAxisValueStatic(String comparisonAxis, EvaluationRunEntity run) {
        return switch (comparisonAxis) {
            case COMPARISON_AXIS_EMBEDDING -> nullToEmpty(run.getEmbeddingModelId());
            case COMPARISON_AXIS_PRESET -> resolvePresetCode(run);
            default -> nullToEmpty(run.getLlmModelId());
        };
    }

    @SuppressWarnings("unchecked")
    private static String resolvePresetCode(EvaluationRunEntity run) {
        if (run.getAggregatesJson() != null) {
            Object codes = run.getAggregatesJson().get(BenchmarkRunOrchestrator.AGG_KEY_REQUESTED_PRESET_CODES);
            if (codes instanceof List<?> list && !list.isEmpty()) {
                Object first = list.getFirst();
                if (first != null) {
                    return String.valueOf(first).trim();
                }
            }
        }
        return "";
    }

    private static String humanModelLabel(EvaluationRunEntity run) {
        if (run.getLlmModelId() != null && !run.getLlmModelId().isBlank()) {
            return run.getLlmModelId().trim();
        }
        if (run.getEmbeddingModelId() != null && !run.getEmbeddingModelId().isBlank()) {
            return run.getEmbeddingModelId().trim();
        }
        return "";
    }

    private static String humanPresetLabel(EvaluationRunEntity run) {
        String code = resolvePresetCode(run);
        return code.isBlank() ? "" : code;
    }

    private static UUID knowledgeBaseId(EvaluationRunEntity run) {
        if (run == null || run.getEvaluationCorpus() == null) {
            return null;
        }
        return run.getEvaluationCorpus().getId();
    }

    private static BenchmarkKind resolveBenchmarkKind(List<EvaluationRunEntity> runs) {
        if (runs == null || runs.isEmpty() || runs.getFirst().getBenchmarkKind() == null) {
            return null;
        }
        try {
            return BenchmarkKind.valueOf(runs.getFirst().getBenchmarkKind());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String humanCorpusName(EvaluationRunEntity run) {
        EvaluationCorpusEntity corpus = run.getEvaluationCorpus();
        if (corpus != null && corpus.getName() != null && !corpus.getName().isBlank()) {
            String name = corpus.getName().trim();
            if (name.toLowerCase().contains("corpus")) {
                return "Lab knowledge base";
            }
            return name;
        }
        return "";
    }

    private static String humanDatasetName(EvaluationRunEntity run) {
        EvaluationDatasetEntity dataset = run.getDataset();
        if (dataset != null && dataset.getName() != null && !dataset.getName().isBlank()) {
            return dataset.getName().trim();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static String resolveFailureReason(EvaluationRunEntity run, Map<String, Object> bucket) {
        if (run.getStatus() != null && "ERROR".equals(run.getStatus().name())) {
            return run.getStatus().name();
        }
        Map<String, Object> outcomeCounts = (Map<String, Object>) bucket.getOrDefault("outcomeCounts", Map.of());
        long failed = longNum(outcomeCounts.get(BenchmarkItemOutcome.FAILED.name()));
        if (failed > 0) {
            return "ITEMS_FAILED";
        }
        return "";
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
        out.put("axisValue", groupValue != null ? groupValue : "");
        @SuppressWarnings("unchecked")
        Map<String, Object> outcomeCounts = (Map<String, Object>) bucket.getOrDefault("outcomeCounts", Map.of());
        long executed = longNum(outcomeCounts.get(BenchmarkItemOutcome.EXECUTED.name()));
        long failed = longNum(outcomeCounts.get(BenchmarkItemOutcome.FAILED.name()));
        long notSupported = longNum(outcomeCounts.get(BenchmarkItemOutcome.NOT_SUPPORTED.name()));
        long skipped = longNum(outcomeCounts.get(BenchmarkItemOutcome.SKIPPED.name()));
        long total = outcomeCounts.values().stream().mapToLong(LabCampaignService::longNum).sum();
        out.put("totalItems", total);
        out.put("executed", executed);
        out.put("failed", failed);
        out.put("notSupported", notSupported);
        out.put("skipped", skipped);
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\"", "\"\"");
        boolean needsQuotes = t.contains(",") || t.contains("\n") || t.contains("\r") || t.contains("\"");
        return needsQuotes ? "\"" + t + "\"" : t;
    }

    private record CampaignContext(String campaignType, String comparisonAxis, boolean comparativeMode, int axisCount) {}
}
