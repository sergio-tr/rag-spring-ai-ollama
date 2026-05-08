package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.interfaces.rest.dto.CompareRunsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationResultItemDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationRunDetailDto;
import com.uniovi.rag.service.evaluation.mvp.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.service.evaluation.mvp.BenchmarkMvpRollupCalculator;
import com.uniovi.rag.service.evaluation.mvp.BenchmarkMvpSchema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Read model for canonical {@code evaluation_run} / {@code evaluation_result}; CSV export §8.6 (#META line).
 */
@Service
public class LabEvaluationRunService {

    static final List<String> MVP_ITEMS_CSV_COLUMNS =
            List.of(
                    "mvpSchemaVersion",
                    "itemId",
                    "benchmarkKind",
                    "queryType",
                    "difficulty",
                    "datasetQuestionId",
                    "recallAt1",
                    "recallAt3",
                    "recallAt5",
                    "mrr",
                    "retrievedCount",
                    "goldFound",
                    "normalizedExactMatch",
                    "containsExpectedAnswer",
                    "answerLength",
                    "semanticScore",
                    "latencyMs",
                    "modelId",
                    "embeddingModelId",
                    "presetCode",
                    "outcome",
                    "failureCode",
                    "unsupportedReason",
                    "retrievalGoldMode",
                    "goldChunkIds",
                    "goldDocumentIds",
                    "retrievedChunkIds",
                    "retrievedDocumentIds",
                    "groupKey",
                    "indexCompatibilityStatus",
                    "requiresReindex",
                    "indexSnapshotId",
                    "indexProfileHash",
                    "presetIndexRequirements",
                    "activeSnapshotCapabilities");

    /** Stable MVP CSV columns (shared by comparison exports and unit tests). */
    public static List<String> MVP_ITEMS_CSV_COLUMNS_FOR_TESTS() {
        return MVP_ITEMS_CSV_COLUMNS;
    }

    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationResultRepository evaluationResultRepository;
    private final ObjectMapper objectMapper;

    public LabEvaluationRunService(
            EvaluationRunRepository evaluationRunRepository,
            EvaluationResultRepository evaluationResultRepository,
            ObjectMapper objectMapper) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationResultRepository = evaluationResultRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public EvaluationRunDetailDto getRun(UUID userId, UUID runId) {
        return toDetail(requireRun(userId, runId));
    }

    @Transactional(readOnly = true)
    public List<EvaluationResultItemDto> listItems(UUID userId, UUID runId) {
        requireRun(userId, runId);
        return evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId).stream()
                .map(LabEvaluationRunService::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompareRunsResponseDto compare(UUID userId, UUID runA, UUID runB) {
        EvaluationRunEntity a = requireRun(userId, runA);
        EvaluationRunEntity b = requireRun(userId, runB);
        List<String> reasons = new ArrayList<>();
        if (!Objects.equals(a.getBenchmarkKind(), b.getBenchmarkKind())) {
            reasons.add("benchmark_kind mismatch");
        }
        if (!Objects.equals(a.getDatasetSha256(), b.getDatasetSha256())) {
            reasons.add("dataset_sha256 mismatch");
        }
        if (!Objects.equals(a.getRunKind(), b.getRunKind())) {
            reasons.add("run_kind mismatch");
        }
        if (!Objects.equals(a.getWorkflowSchemaVersion(), b.getWorkflowSchemaVersion())) {
            reasons.add("workflow_schema_version mismatch");
        }
        BenchmarkKind bk = parseKind(a.getBenchmarkKind());
        if (bk == BenchmarkKind.RAG_PRESET_END_TO_END) {
            UUID pa = a.getPreset() != null ? a.getPreset().getId() : null;
            UUID pb = b.getPreset() != null ? b.getPreset().getId() : null;
            if (!Objects.equals(pa, pb)) {
                reasons.add("preset_id mismatch");
            }
        }
        if (bk != null && needsIndexFingerprint(bk)) {
            UUID ia = a.getIndexSnapshot() != null ? a.getIndexSnapshot().getId() : null;
            UUID ib = b.getIndexSnapshot() != null ? b.getIndexSnapshot().getId() : null;
            if (!Objects.equals(ia, ib)) {
                reasons.add("index_snapshot_id mismatch");
            }
            if (!Objects.equals(a.getIndexSignatureHash(), b.getIndexSignatureHash())) {
                reasons.add("index_signature_hash mismatch");
            }
        }
        return new CompareRunsResponseDto(reasons.isEmpty(), reasons, runA, runB);
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        List<EvaluationResultEntity> items =
                evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId);
        String meta;
        try {
            meta = objectMapper.writeValueAsString(toDetail(run));
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not serialize run meta");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("#META:").append(meta).append('\n');
        sb.append(
                "id,question_text,expected_answer,actual_answer,correctness,query_type,latency_ms,benchmark_kind,preset_code,preset_label,outcome,reason,metrics_json\n");
        for (EvaluationResultEntity it : items) {
            sb.append(csvEscape(uuidStr(it.getId())));
            sb.append(',');
            sb.append(csvEscape(it.getQuestionText()));
            sb.append(',');
            sb.append(csvEscape(it.getExpectedAnswer()));
            sb.append(',');
            sb.append(csvEscape(it.getActualAnswer()));
            sb.append(',');
            sb.append(it.getCorrectness() != null ? it.getCorrectness().toString() : "");
            sb.append(',');
            sb.append(csvEscape(it.getQueryType()));
            sb.append(',');
            sb.append(it.getLatencyMs() != null ? it.getLatencyMs().toString() : "");
            sb.append(',');
            sb.append(csvEscape(it.getBenchmarkKind()));
            sb.append(',');
            sb.append(csvEscape(metricStr(it, BenchmarkResultRowKeys.PRESET_CODE)));
            sb.append(',');
            sb.append(csvEscape(metricStr(it, BenchmarkResultRowKeys.PRESET_LABEL)));
            sb.append(',');
            sb.append(csvEscape(metricStr(it, BenchmarkResultRowKeys.ITEM_OUTCOME)));
            sb.append(',');
            sb.append(csvEscape(metricStr(it, BenchmarkResultRowKeys.REASON)));
            sb.append(',');
            try {
                sb.append(csvEscape(
                        it.getMetricsPayload() != null ? objectMapper.writeValueAsString(it.getMetricsPayload()) : ""));
            } catch (JsonProcessingException e) {
                sb.append(csvEscape(""));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportJsonBundle(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("run", toDetailMap(run));
        out.put(
                "items",
                evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId).stream()
                        .map(LabEvaluationRunService::toItemMap)
                        .toList());
        return out;
    }

    /**
     * Thesis MVP export: one JSON object per item including nested {@code mvp} metrics ({@code items.json} bundle).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportMvpItemsJsonBundle(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mvpSchemaVersion", BenchmarkMvpSchema.VERSION);
        out.put("run", toDetailMap(run));
        out.put("items", buildMvpItemPayload(runId, run));
        return out;
    }

    /** MVP flat CSV ({@code items.csv}) — UTF-8, header row only (no legacy {@code #META} line). */
    @Transactional(readOnly = true)
    public String exportMvpItemsCsv(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        List<EvaluationResultEntity> items =
                evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId);
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", MVP_ITEMS_CSV_COLUMNS)).append('\n');
        for (EvaluationResultEntity it : items) {
            Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(it, run);
            List<String> cells =
                    MVP_ITEMS_CSV_COLUMNS.stream().map(h -> csvEscape(row.getOrDefault(h, ""))).toList();
            sb.append(String.join(",", cells)).append('\n');
        }
        return sb.toString();
    }

    /** MVP rollups ({@code rollups.json}) with explicit outcome partitioning. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportMvpRollupsJson(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        List<EvaluationResultEntity> items =
                evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId);
        return BenchmarkMvpRollupCalculator.build(items, run);
    }

    private List<Map<String, Object>> buildMvpItemPayload(UUID runId, EvaluationRunEntity run) {
        return evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(runId).stream()
                .map(
                        e -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", e.getId());
                            row.put("benchmarkKind", e.getBenchmarkKind());
                            row.put("questionText", e.getQuestionText());
                            row.put("expectedAnswer", e.getExpectedAnswer());
                            row.put("actualAnswer", e.getActualAnswer());
                            row.put("correctness", e.getCorrectness());
                            row.put("evaluatedAt", e.getEvaluatedAt());
                            row.put("metricsPayload", e.getMetricsPayload());
                            row.put("mvp", BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, run));
                            return row;
                        })
                .toList();
    }

    private EvaluationRunEntity requireRun(UUID userId, UUID runId) {
        return evaluationRunRepository
                .findByIdAndUser_Id(runId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private static EvaluationRunDetailDto toDetail(EvaluationRunEntity e) {
        return new EvaluationRunDetailDto(
                e.getId(),
                e.getName(),
                e.getStatus().name(),
                e.getBenchmarkKind(),
                e.getRunKind(),
                e.getWorkflowSchemaVersion(),
                e.getDatasetSha256(),
                e.getDataset() != null ? e.getDataset().getId() : null,
                e.getAsyncTask() != null ? e.getAsyncTask().getId() : null,
                e.getResolvedConfigSnapshot() != null ? e.getResolvedConfigSnapshot().getId() : null,
                e.getIndexSnapshot() != null ? e.getIndexSnapshot().getId() : null,
                e.getIndexSignatureHash(),
                e.getPreset() != null ? e.getPreset().getId() : null,
                e.getLlmModelId(),
                e.getEmbeddingModelId(),
                e.getClassifierModelId(),
                e.getAggregatesJson(),
                e.getCreatedAt(),
                e.getCompletedAt());
    }

    private Map<String, Object> toDetailMap(EvaluationRunEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("status", e.getStatus().name());
        m.put("benchmarkKind", e.getBenchmarkKind());
        m.put("runKind", e.getRunKind());
        m.put("workflowSchemaVersion", e.getWorkflowSchemaVersion());
        m.put("datasetSha256", e.getDatasetSha256());
        m.put("datasetId", e.getDataset() != null ? e.getDataset().getId() : null);
        m.put("asyncTaskId", e.getAsyncTask() != null ? e.getAsyncTask().getId() : null);
        m.put("aggregatesJson", e.getAggregatesJson());
        m.put("createdAt", e.getCreatedAt());
        m.put("completedAt", e.getCompletedAt());
        return m;
    }

    private static Map<String, Object> toItemMap(EvaluationResultEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("questionText", e.getQuestionText());
        m.put("expectedAnswer", e.getExpectedAnswer());
        m.put("actualAnswer", e.getActualAnswer());
        m.put("correctness", e.getCorrectness());
        m.put("queryType", e.getQueryType());
        m.put("latencyMs", e.getLatencyMs());
        m.put("benchmarkKind", e.getBenchmarkKind());
        m.put("metricsPayload", e.getMetricsPayload());
        m.put("evaluatedAt", e.getEvaluatedAt());
        return m;
    }

    private static EvaluationResultItemDto toItem(EvaluationResultEntity e) {
        return new EvaluationResultItemDto(
                e.getId(),
                e.getQuestionText(),
                e.getExpectedAnswer(),
                e.getActualAnswer(),
                e.getCorrectness(),
                e.getQueryType(),
                e.getLatencyMs(),
                e.getBenchmarkKind(),
                e.getMetricsPayload(),
                e.getEvaluatedAt());
    }

    private static boolean needsIndexFingerprint(BenchmarkKind bk) {
        return bk == BenchmarkKind.EMBEDDING_RETRIEVAL || bk == BenchmarkKind.RAG_PRESET_END_TO_END;
    }

    private static BenchmarkKind parseKind(String s) {
        if (s == null) {
            return null;
        }
        try {
            return BenchmarkKind.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String uuidStr(UUID id) {
        return id != null ? id.toString() : "";
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

    private static String metricStr(EvaluationResultEntity item, String key) {
        if (item.getMetricsPayload() == null || !item.getMetricsPayload().containsKey(key)) {
            return "";
        }
        Object v = item.getMetricsPayload().get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
