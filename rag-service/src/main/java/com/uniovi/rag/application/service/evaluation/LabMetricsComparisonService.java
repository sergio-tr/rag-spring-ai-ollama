package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpRollupCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Metrics comparison for thesis workflows: compares N compatible runs and exports summary + long-form table CSV.
 */
@Service
public class LabMetricsComparisonService {

    private static final List<String> TABLE_COLUMNS =
            List.of(
                    "dimensionType",
                    "dimensionValue",
                    "runId",
                    "benchmarkKind",
                    "datasetSha256",
                    "queryTypeFilter",
                    "difficultyFilter",
                    "executedCount",
                    "notSupportedCount",
                    "failedCount",
                    "skippedCount",
                    "meanNormalizedExactMatch",
                    "meanSemanticScoreWhereJudgePresent",
                    "meanLatencyMsWherePresent",
                    "meanRecallAt1",
                    "meanRecallAt3",
                    "meanRecallAt5",
                    "meanMrr");

    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationResultRepository evaluationResultRepository;

    public LabMetricsComparisonService(
            EvaluationRunRepository evaluationRunRepository,
            EvaluationResultRepository evaluationResultRepository) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationResultRepository = evaluationResultRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compareMetrics(
            UUID userId, List<UUID> runIds, List<String> queryTypes, List<String> difficulties) {
        List<UUID> ids = normalizeRunIds(runIds);
        if (ids.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least two runIds");
        }

        List<EvaluationRunEntity> runs = new ArrayList<>(evaluationRunRepository.findByIdInAndUser_Id(ids, userId));
        if (runs.size() != ids.size()) {
            Set<UUID> found = runs.stream().map(EvaluationRunEntity::getId).collect(Collectors.toSet());
            List<UUID> missing = ids.stream().filter(x -> !found.contains(x)).toList();
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Runs not found: " + missing);
        }
        runs.sort(Comparator.comparing(EvaluationRunEntity::getCreatedAt));

        Compatibility compat = checkCompatibility(runs);
        if (!compat.reasons.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Runs are not comparable: " + String.join("; ", compat.reasons));
        }

        BenchmarkKind kind = BenchmarkKind.valueOf(Objects.requireNonNull(runs.get(0).getBenchmarkKind()));
        String dimensionType = dimensionType(kind);
        String byKey = rollupGroupKey(kind);

        List<ComparisonRow> rows = new ArrayList<>();
        Map<UUID, Map<String, Object>> rollupsByRun = new LinkedHashMap<>();
        for (EvaluationRunEntity run : runs) {
            List<EvaluationResultEntity> items =
                    evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
            List<EvaluationResultEntity> filtered = filterItems(items, queryTypes, difficulties);
            Map<String, Object> rollups = BenchmarkMvpRollupCalculator.build(filtered, run);
            rollupsByRun.put(run.getId(), rollups);

            Map<String, Object> groups = readMap(rollups.get(byKey)).orElse(Map.of());
            for (Map.Entry<String, Object> e : groups.entrySet()) {
                Map<String, Object> bucket = readMap(e.getValue()).orElse(Map.of());
                rows.add(ComparisonRow.fromBucket(
                        dimensionType,
                        e.getKey(),
                        run,
                        compat.datasetSha256,
                        queryTypes,
                        difficulties,
                        bucket));
            }
        }

        BestCandidates best = BestCandidates.compute(rows);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("comparable", true);
        out.put("benchmarkKind", kind.name());
        out.put("datasetSha256", compat.datasetSha256);
        out.put("workflowSchemaVersion", compat.workflowSchemaVersion);
        out.put("dimensionType", dimensionType);
        out.put("filters", Map.of("queryTypes", queryTypes != null ? queryTypes : List.of(), "difficulties", difficulties != null ? difficulties : List.of()));
        out.put(
                "runs",
                runs.stream()
                        .map(
                                r -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("runId", r.getId());
                                    m.put("createdAt", r.getCreatedAt());
                                    m.put("llmModelId", r.getLlmModelId());
                                    m.put("embeddingModelId", r.getEmbeddingModelId());
                                    m.put("campaignId", r.getCampaign() != null ? r.getCampaign().getId() : null);
                                    return m;
                                })
                        .toList());
        out.put("tableRows", rows.stream().map(ComparisonRow::toMap).toList());
        out.put("bestCandidates", best.toMap());
        out.put("rollupsByRun", rollupsByRun);
        return out;
    }

    @Transactional(readOnly = true)
    public String exportComparisonTableCsv(
            UUID userId, List<UUID> runIds, List<String> queryTypes, List<String> difficulties) {
        Map<String, Object> summary = compareMetrics(userId, runIds, queryTypes, difficulties);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) summary.getOrDefault("tableRows", List.of());
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", TABLE_COLUMNS)).append('\n');
        for (Map<String, Object> r : rows) {
            List<String> cells = TABLE_COLUMNS.stream().map(c -> csvEscape(val(r.get(c)))).toList();
            sb.append(String.join(",", cells)).append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportComparisonItemsCsv(
            UUID userId, List<UUID> runIds, List<String> queryTypes, List<String> difficulties) {
        List<UUID> ids = normalizeRunIds(runIds);
        List<EvaluationRunEntity> runs = new ArrayList<>(evaluationRunRepository.findByIdInAndUser_Id(ids, userId));
        if (runs.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more runs not found");
        }
        Compatibility compat = checkCompatibility(runs);
        if (!compat.reasons.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Runs are not comparable: " + String.join("; ", compat.reasons));
        }
        Map<UUID, EvaluationRunEntity> runById = runs.stream().collect(Collectors.toMap(EvaluationRunEntity::getId, r -> r));
        List<EvaluationResultEntity> allItems = evaluationResultRepository.findByRun_IdInOrderByEvaluatedAtAsc(ids);
        List<EvaluationResultEntity> filtered = filterItems(allItems, queryTypes, difficulties);

        List<String> baseCols = new ArrayList<>();
        baseCols.add("runId");
        baseCols.addAll(LabEvaluationRunService.MVP_ITEMS_CSV_COLUMNS_FOR_TESTS());
        // The helper returns stable columns from the existing MVP export.
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", baseCols)).append('\n');
        for (EvaluationResultEntity it : filtered) {
            EvaluationRunEntity run = runById.get(it.getRun().getId());
            Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(it, run);
            List<String> cells = new ArrayList<>();
            cells.add(csvEscape(val(it.getRun().getId())));
            for (String c : LabEvaluationRunService.MVP_ITEMS_CSV_COLUMNS_FOR_TESTS()) {
                cells.add(csvEscape(row.getOrDefault(c, "")));
            }
            sb.append(String.join(",", cells)).append('\n');
        }
        return sb.toString();
    }

    private static List<EvaluationResultEntity> filterItems(
            List<EvaluationResultEntity> items, List<String> queryTypes, List<String> difficulties) {
        if ((queryTypes == null || queryTypes.isEmpty()) && (difficulties == null || difficulties.isEmpty())) {
            return items;
        }
        Set<String> qt = queryTypes != null ? new LinkedHashSet<>(queryTypes) : Set.of();
        Set<String> diff = difficulties != null ? new LinkedHashSet<>(difficulties) : Set.of();
        return items.stream()
                .filter(
                        it -> {
                            if (!qt.isEmpty()) {
                                String q = it.getQueryType() != null ? it.getQueryType().trim() : "";
                                if (!qt.contains(q)) {
                                    return false;
                                }
                            }
                            if (!diff.isEmpty()) {
                                String d = metricStr(it, BenchmarkResultRowKeys.DIFFICULTY);
                                if (!diff.contains(d)) {
                                    return false;
                                }
                            }
                            return true;
                        })
                .toList();
    }

    private static Compatibility checkCompatibility(List<EvaluationRunEntity> runs) {
        Compatibility c = new Compatibility();
        EvaluationRunEntity first = runs.get(0);
        c.datasetSha256 = first.getDatasetSha256();
        c.workflowSchemaVersion = first.getWorkflowSchemaVersion();
        c.benchmarkKind = first.getBenchmarkKind();
        c.runKind = first.getRunKind();
        c.indexSignatureHash = first.getIndexSignatureHash();
        c.indexSnapshotId = first.getIndexSnapshot() != null ? first.getIndexSnapshot().getId() : null;

        for (EvaluationRunEntity r : runs) {
            if (!Objects.equals(c.benchmarkKind, r.getBenchmarkKind())) {
                c.reasons.add("benchmark_kind mismatch");
            }
            if (!Objects.equals(c.datasetSha256, r.getDatasetSha256())) {
                c.reasons.add("dataset_sha256 mismatch");
            }
            if (!Objects.equals(c.runKind, r.getRunKind())) {
                c.reasons.add("run_kind mismatch");
            }
            if (!Objects.equals(c.workflowSchemaVersion, r.getWorkflowSchemaVersion())) {
                c.reasons.add("workflow_schema_version mismatch");
            }
        }
        BenchmarkKind bk = c.benchmarkKind != null ? BenchmarkKind.valueOf(c.benchmarkKind) : null;
        if (bk != null && (bk == BenchmarkKind.EMBEDDING_RETRIEVAL || bk == BenchmarkKind.RAG_PRESET_END_TO_END)) {
            for (EvaluationRunEntity r : runs) {
                UUID rs = r.getIndexSnapshot() != null ? r.getIndexSnapshot().getId() : null;
                if (!Objects.equals(c.indexSnapshotId, rs)) {
                    c.reasons.add("index_snapshot_id mismatch");
                }
                if (!Objects.equals(c.indexSignatureHash, r.getIndexSignatureHash())) {
                    c.reasons.add("index_signature_hash mismatch");
                }
            }
        }
        c.reasons = c.reasons.stream().distinct().toList();
        return c;
    }

    private static String rollupGroupKey(BenchmarkKind kind) {
        return switch (kind) {
            case LLM_JUDGE_QA -> "byLlmModel";
            case EMBEDDING_RETRIEVAL -> "byEmbeddingModel";
            case RAG_PRESET_END_TO_END -> "byPreset";
            default -> "byLlmModel";
        };
    }

    private static String dimensionType(BenchmarkKind kind) {
        return switch (kind) {
            case LLM_JUDGE_QA -> "LLM_MODEL";
            case EMBEDDING_RETRIEVAL -> "EMBEDDING_MODEL";
            case RAG_PRESET_END_TO_END -> "PRESET_CODE";
            default -> "UNKNOWN";
        };
    }

    private static List<UUID> normalizeRunIds(List<UUID> runIds) {
        if (runIds == null) {
            return List.of();
        }
        return runIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static Optional<Map<String, Object>> readMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mm = (Map<String, Object>) m;
            return Optional.of(mm);
        }
        return Optional.empty();
    }

    private static long countOutcome(Map<String, Object> bucket, String key) {
        Map<String, Object> outcomeCounts = readMap(bucket.get("outcomeCounts")).orElse(Map.of());
        Object v = outcomeCounts.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static Double readDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static String metricStr(EvaluationResultEntity item, String key) {
        if (item.getMetricsPayload() == null || !item.getMetricsPayload().containsKey(key)) {
            return "";
        }
        Object v = item.getMetricsPayload().get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String val(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String csvEscape(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private static final class Compatibility {
        String benchmarkKind;
        String datasetSha256;
        String runKind;
        String workflowSchemaVersion;
        UUID indexSnapshotId;
        String indexSignatureHash;
        List<String> reasons = new ArrayList<>();
    }

    private record ComparisonRow(
            String dimensionType,
            String dimensionValue,
            UUID runId,
            String benchmarkKind,
            String datasetSha256,
            String queryTypeFilter,
            String difficultyFilter,
            long executedCount,
            long notSupportedCount,
            long failedCount,
            long skippedCount,
            Double meanNormalizedExactMatch,
            Double meanSemanticScoreWhereJudgePresent,
            Double meanLatencyMsWherePresent,
            Double meanRecallAt1,
            Double meanRecallAt3,
            Double meanRecallAt5,
            Double meanMrr) {

        static ComparisonRow fromBucket(
                String dimensionType,
                String dimensionValue,
                EvaluationRunEntity run,
                String datasetSha256,
                List<String> queryTypes,
                List<String> difficulties,
                Map<String, Object> bucket) {
            Map<String, Object> onExecuted = readMap(bucket.get("onExecuted")).orElse(Map.of());
            Map<String, Object> retrieval = readMap(bucket.get("retrievalOnExecutedWhereApplicable")).orElse(Map.of());
            String qt = queryTypes != null && !queryTypes.isEmpty() ? String.join("|", queryTypes) : "";
            String diff = difficulties != null && !difficulties.isEmpty() ? String.join("|", difficulties) : "";
            return new ComparisonRow(
                    dimensionType,
                    dimensionValue,
                    run.getId(),
                    run.getBenchmarkKind(),
                    datasetSha256,
                    qt,
                    diff,
                    countOutcome(bucket, "EXECUTED"),
                    countOutcome(bucket, "NOT_SUPPORTED"),
                    countOutcome(bucket, "FAILED"),
                    countOutcome(bucket, "SKIPPED"),
                    readDouble(onExecuted, "meanNormalizedExactMatch"),
                    readDouble(onExecuted, "meanSemanticScoreWhereJudgePresent"),
                    readDouble(onExecuted, "meanLatencyMsWherePresent"),
                    readDouble(retrieval, "meanRecallAt1"),
                    readDouble(retrieval, "meanRecallAt3"),
                    readDouble(retrieval, "meanRecallAt5"),
                    readDouble(retrieval, "meanMrr"));
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dimensionType", dimensionType);
            m.put("dimensionValue", dimensionValue);
            m.put("runId", runId);
            m.put("benchmarkKind", benchmarkKind);
            m.put("datasetSha256", datasetSha256);
            m.put("queryTypeFilter", queryTypeFilter);
            m.put("difficultyFilter", difficultyFilter);
            m.put("executedCount", executedCount);
            m.put("notSupportedCount", notSupportedCount);
            m.put("failedCount", failedCount);
            m.put("skippedCount", skippedCount);
            m.put("meanNormalizedExactMatch", meanNormalizedExactMatch);
            m.put("meanSemanticScoreWhereJudgePresent", meanSemanticScoreWhereJudgePresent);
            m.put("meanLatencyMsWherePresent", meanLatencyMsWherePresent);
            m.put("meanRecallAt1", meanRecallAt1);
            m.put("meanRecallAt3", meanRecallAt3);
            m.put("meanRecallAt5", meanRecallAt5);
            m.put("meanMrr", meanMrr);
            return m;
        }
    }

    private static final class BestCandidates {
        private final Map<String, Object> bestByMetric = new LinkedHashMap<>();

        static BestCandidates compute(List<ComparisonRow> rows) {
            BestCandidates out = new BestCandidates();
            out.pickMax(rows, "meanNormalizedExactMatch", r -> r.meanNormalizedExactMatch);
            out.pickMax(rows, "meanRecallAt1", r -> r.meanRecallAt1);
            out.pickMax(rows, "meanMrr", r -> r.meanMrr);
            out.pickMin(rows, "meanLatencyMsWherePresent", r -> r.meanLatencyMsWherePresent);
            return out;
        }

        void pickMax(List<ComparisonRow> rows, String metric, Function<ComparisonRow, Double> fn) {
            rows.stream()
                    .filter(r -> fn.apply(r) != null)
                    .max(Comparator.comparing(fn))
                    .ifPresent(r -> bestByMetric.put(metric, r.toMap()));
        }

        void pickMin(List<ComparisonRow> rows, String metric, Function<ComparisonRow, Double> fn) {
            rows.stream()
                    .filter(r -> fn.apply(r) != null)
                    .min(Comparator.comparing(fn))
                    .ifPresent(r -> bestByMetric.put(metric, r.toMap()));
        }

        Map<String, Object> toMap() {
            return bestByMetric;
        }
    }
}

