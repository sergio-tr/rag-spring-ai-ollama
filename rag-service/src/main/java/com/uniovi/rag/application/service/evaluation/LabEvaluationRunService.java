package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.EvaluationResultRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.interfaces.rest.dto.CampaignChildRunSummaryDto;
import com.uniovi.rag.interfaces.rest.dto.CompareRunsResponseDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationResultItemDto;
import com.uniovi.rag.interfaces.rest.dto.EvaluationRunDetailDto;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpRollupCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpSchema;
import com.uniovi.rag.configuration.RagApiPathProperties;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.interfaces.rest.dto.LatestLabRunRecoveryDto;
import org.springframework.data.domain.PageRequest;
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
                    "evaluationRunId",
                    "evaluationDatasetId",
                    "evaluationDatasetSha256",
                    "projectId",
                    "corpusDocumentSet",
                    "resolvedConfigSnapshotId",
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
                    "correctness",
                    "llmJudgeScore",
                    "hallucinationRate",
                    "faithfulness",
                    "sourceSupport",
                    "dateCorrectness",
                    "latencyMs",
                    "modelId",
                    "embeddingModelId",
                    "embeddingDimensions",
                    "embeddingCompatibilityStatus",
                    "embeddingCompatibilityErrorCode",
                    "embeddingCompatibilityReason",
                    "classifierModelId",
                    "presetCode",
                    "outcome",
                    "failureCode",
                    "unsupportedReason",
                    "skipReasonCode",
                    "skipReason",
                    "runPlanVersion",
                    "retrievalGoldMode",
                    "goldChunkIds",
                    "goldDocumentIds",
                    "retrievedChunkIds",
                    "retrievedDocumentIds",
                    "retrievalDenseCandidateCount",
                    "retrievalAfterFilterCount",
                    "contextChunkCount",
                    "promptContextCharCount",
                    "sourceCount",
                    "advancedRetrievalApplied",
                    "advancedRetrievalStrategy",
                    "denseCandidateCount",
                    "sparseCandidateCount",
                    "hybridCandidateCount",
                    "mergedCandidateCount",
                    "dedupedCandidateCount",
                    "rerankedCandidateCount",
                    "finalContextChunkCount",
                    "rerankApplied",
                    "rerankChangedOrder",
                    "compressionApplied",
                    "compressedContextCharCount",
                    "advancedRetrievalFallbackReason",
                    "candidateOrigins",
                    "sparseRetrievalStatus",
                    "hybridApplied",
                    "retrievalRoute",
                    "retrievalMode",
                    "rerankNoopReason",
                    "originalContextCharCount",
                    "effectiveContextPresent",
                    "answerability",
                    "answerabilitySource",
                    "expectedAnswerPresent",
                    "queryTypeExpected",
                    "queryTypePredicted",
                    "queryTypeMatch",
                    "abstained",
                    "abstentionReason",
                    "abstentionCorrectness",
                    "finalScore",
                    "structuredScore",
                    "structuredScoreStatus",
                    "analysisSemanticScore",
                    "abstentionScore",
                    "exactMatchNormalized",
                    "expectedAnswerContained",
                    "countMatch",
                    "booleanMatch",
                    "dateMatch",
                    "durationMatch",
                    "fieldMatchScore",
                    "entityPrecision",
                    "entityRecall",
                    "entityF1",
                    "listPrecision",
                    "listRecall",
                    "listF1",
                    "scoreUnavailableReason",
                    "finalScoreAvailable",
                    "finalScoreStatus",
                    "retrievalCoverageStatus",
                    "retrievalQualityStatus",
                    "recallAtK",
                    "ndcg",
                    "classifierStatus",
                    "classifierConfidence",
                    "classifierModelId",
                    "classifierLabelSetHash",
                    "classifierFallback",
                    "classifierFallbackReason",
                    "routeSuppressedByClassifier",
                    "routeSuppressedReason",
                    "heuristicRouteUsed",
                    "groupKey",
                    "deterministicToolRoutingEnabled",
                    "deterministicToolRoute",
                    "functionCallingUsed",
                    "functionCallAttempted",
                    "functionCallName",
                    "functionCallArgumentsValid",
                    "functionCallSucceeded",
                    "functionCallFallbackReason",
                    "functionResultUsedAsFinal",
                    "functionResultUsedAsContext",
                    "functionCallRoute",
                    "executionRoute",
                    "advisorEnabled",
                    "advisorRoute",
                    "advisorAttempted",
                    "advisorApplied",
                    "advisorName",
                    "advisorType",
                    "advisorContributionType",
                    "advisorChangedQuery",
                    "advisorChangedContext",
                    "advisorChangedPrompt",
                    "advisorValidatedAnswer",
                    "advisorFallbackReason",
                    "advisorResultUsed",
                    "toolApplicable",
                    "toolSelected",
                    "toolName",
                    "toolExecuted",
                    "toolSucceeded",
                    "toolFallbackReason",
                    "toolResultUsedAsFinal",
                    "queryTypeSource",
                    "toolCoverageStatus",
                    "routingRouteKind",
                    "indexCompatibilityStatus",
                    "requiresReindex",
                    "indexSnapshotId",
                    "indexProfileHash",
                    "effectiveGroupSnapshotId",
                    "groupIndexProfileHash",
                    "reindexAction",
                    "reindexStatus",
                    "forcedSnapshotSelection",
                    "reindexEventId",
                    "reindexStartedAt",
                    "reindexCompletedAt",
                    "reindexErrorCode",
                    "reindexErrorReason",
                    "presetIndexRequirements",
                    "activeSnapshotCapabilities",
                    "presetLabel",
                    "productPresetId",
                    "protocolStageIndex",
                    "presetStage",
                    "presetLadderScope",
                    "requiresMultiTurn",
                    "singleTurnBenchmarkSelectable",
                    "comparableSingleTurnMetric",
                    "benchmarkSupportStatus",
                    "workflowName",
                    "activeFeatures",
                    "useRetrieval",
                    "naiveFullCorpusInPromptEnabled",
                    "materializationStrategy",
                    "metadataEnabled",
                    "expansionEnabled",
                    "nerEnabled",
                    "reasoningEnabled",
                    "toolsEnabled",
                    "functionCallingEnabled",
                    "rankerEnabled",
                    "postRetrievalEnabled",
                    "useAdvisor",
                    "adaptiveRoutingEnabled",
                    "judgeEnabled",
                    "clarificationEnabled",
                    "memoryEnabled",
                    "corpusRequired",
                    "corpusAvailable",
                    "corpusChars",
                    "corpusTruncated",
                    "selectedSnapshotIds",
                    "groundingPolicy",
                    "campaignId",
                    "childRunId",
                    "comparisonAxis",
                    "axisValue",
                    "comparisonLabel",
                    "presetKey",
                    "presetOrder",
                    "modelLabel",
                    "corpusId",
                    "corpusName",
                    "datasetId",
                    "datasetName",
                    "singleTurnSupported",
                    "comparableInSingleTurn",
                    "timestamp");

    /** Stable MVP CSV columns (shared by comparison exports and unit tests). */
    public static List<String> MVP_ITEMS_CSV_COLUMNS_FOR_TESTS() {
        return MVP_ITEMS_CSV_COLUMNS;
    }

    private final EvaluationRunRepository evaluationRunRepository;
    private final EvaluationResultRepository evaluationResultRepository;
    private final LabPresetAxisSupport labPresetAxisSupport;
    private final ObjectMapper objectMapper;
    private final RagApiPathProperties apiPathProperties;

    public LabEvaluationRunService(
            EvaluationRunRepository evaluationRunRepository,
            EvaluationResultRepository evaluationResultRepository,
            LabPresetAxisSupport labPresetAxisSupport,
            ObjectMapper objectMapper,
            RagApiPathProperties apiPathProperties) {
        this.evaluationRunRepository = evaluationRunRepository;
        this.evaluationResultRepository = evaluationResultRepository;
        this.labPresetAxisSupport = labPresetAxisSupport;
        this.objectMapper = objectMapper;
        this.apiPathProperties = apiPathProperties;
    }

    /**
     * Latest run for benchmark kind + optional project scope (Lab recovery when no active job).
     * Returns the most recently updated run owned by the user; project filter matches active-job semantics.
     */
    @Transactional(readOnly = true)
    public LatestLabRunRecoveryDto findLatestRunForRecovery(
            UUID userId, BenchmarkKind benchmarkKind, UUID projectIdOrNull) {
        if (userId == null || benchmarkKind == null) {
            return null;
        }
        List<EvaluationRunEntity> recent =
                evaluationRunRepository.findRecentByUserAndBenchmarkKind(
                        userId, benchmarkKind.name(), PageRequest.of(0, 8));
        EvaluationRunEntity match =
                recent.stream()
                        .filter(r -> matchesProjectScope(r, projectIdOrNull))
                        .findFirst()
                        .orElse(null);
        if (match == null) {
            return null;
        }
        return toLatestRecoveryDto(match);
    }

    private static boolean matchesProjectScope(EvaluationRunEntity run, UUID projectIdOrNull) {
        UUID runProject = run.getProject() != null ? run.getProject().getId() : null;
        if (runProject == null) {
            return true;
        }
        if (projectIdOrNull == null) {
            return true;
        }
        return projectIdOrNull.equals(runProject);
    }

    private LatestLabRunRecoveryDto toLatestRecoveryDto(EvaluationRunEntity run) {
        AsyncTaskEntity task = run.getAsyncTask();
        UUID taskId = task != null ? task.getId() : null;
        String status =
                task != null && task.getStatus() != null ? task.getStatus().name() : "UNKNOWN";
        boolean terminal = task != null && task.isTerminal();
        Map<String, Object> result = task != null ? task.getResultJson() : null;
        String base = taskId != null ? jobBasePath(taskId) : null;
        boolean hasResults = result != null && !result.isEmpty();
        UUID campaignId = run.getCampaign() != null ? run.getCampaign().getId() : null;
        Integer persistedItemCount = null;
        List<UUID> childRunIds = List.of();
        if (campaignId != null && run.getUser() != null) {
            List<EvaluationRunEntity> children =
                    evaluationRunRepository.findByCampaignIdAndUserId(campaignId, run.getUser().getId());
            childRunIds = children.stream().map(EvaluationRunEntity::getId).toList();
            persistedItemCount = countCampaignPersistedItems(campaignId, run.getUser().getId());
        } else {
            persistedItemCount = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId()).size();
        }
        return new LatestLabRunRecoveryDto(
                run.getId(),
                taskId,
                run.getBenchmarkKind(),
                run.getProject() != null ? run.getProject().getId() : null,
                status,
                terminal,
                base,
                base != null ? base + "/events" : null,
                result,
                task != null ? task.getStartedAt() : null,
                task != null ? task.getCompletedAt() : null,
                hasResults,
                campaignId,
                persistedItemCount,
                childRunIds);
    }

    private String jobBasePath(UUID taskId) {
        return apiPathProperties.getProductBasePath() + "/lab/jobs/" + taskId;
    }

    @Transactional(readOnly = true)
    public EvaluationRunDetailDto getRun(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        return toDetail(run, userId);
    }

    @Transactional(readOnly = true)
    public List<EvaluationResultItemDto> listItems(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        return listPersistedItems(run, userId).stream().map(LabEvaluationRunService::toItem).toList();
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
        List<EvaluationResultEntity> items = listPersistedItems(run, userId);
        String meta;
        try {
            meta = objectMapper.writeValueAsString(toDetail(run, userId));
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
        out.put("run", toDetailMap(run, userId));
        out.put("items", listPersistedItems(run, userId).stream().map(LabEvaluationRunService::toItemMap).toList());
        return out;
    }

    /**
     * One JSON object per item including nested {@code mvp} metrics ({@code items.json} bundle).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportMvpItemsJsonBundle(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mvpSchemaVersion", BenchmarkMvpSchema.VERSION);
        out.put(
                "scoringNote",
                "Prefer scoreAnswerable for answer-quality; scoreGlobal blends answerability groups. "
                        + "In JSON analysis, check finalScoreAvailable before interpreting finalScore.");
        out.put("run", toDetailMap(run, userId));
        if (run.getCampaign() != null) {
            out.put("campaignId", run.getCampaign().getId());
            out.put("campaignMode", true);
            out.put("campaignPersistedItemCount", countCampaignPersistedItems(run.getCampaign().getId(), userId));
        }
        out.put("items", buildMvpItemPayload(run, userId));
        return out;
    }

    /** MVP flat CSV ({@code items.csv}) — UTF-8, header row only (no {@code #META} line). */
    @Transactional(readOnly = true)
    public String exportMvpItemsCsv(UUID userId, UUID runId) {
        EvaluationRunEntity run = requireRun(userId, runId);
        List<EvaluationResultEntity> items = listPersistedItems(run, userId);
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", MVP_ITEMS_CSV_COLUMNS)).append('\n');
        for (EvaluationResultEntity it : items) {
            EvaluationRunEntity itemRun = it.getRun() != null ? it.getRun() : run;
            Map<String, String> row = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(it, itemRun);
            labPresetAxisSupport.enrichItemExportRow(row, itemRun, it);
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
        List<EvaluationResultEntity> items = listPersistedItems(run, userId);
        Map<String, Object> rollups = BenchmarkMvpRollupCalculator.build(items, run);
        if (run.getCampaign() != null) {
            LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(rollups);
            enriched.put("campaignId", run.getCampaign().getId());
            enriched.put("campaignMode", true);
            enriched.put("campaignPersistedItemCount", countCampaignPersistedItems(run.getCampaign().getId(), userId));
            return Map.copyOf(enriched);
        }
        return rollups;
    }

    private List<Map<String, Object>> buildMvpItemPayload(EvaluationRunEntity run, UUID userId) {
        return listPersistedItems(run, userId).stream()
                .map(
                        e -> {
                            EvaluationRunEntity itemRun =
                                    e.getRun() != null ? e.getRun() : run;
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", e.getId());
                            row.put("benchmarkKind", e.getBenchmarkKind());
                            row.put("questionText", e.getQuestionText());
                            row.put("expectedAnswer", e.getExpectedAnswer());
                            row.put("actualAnswer", e.getActualAnswer());
                            row.put("correctness", e.getCorrectness());
                            row.put("evaluatedAt", e.getEvaluatedAt());
                            row.put("metricsPayload", e.getMetricsPayload());
                            row.put("mvp", BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, itemRun));
                            return row;
                        })
                .toList();
    }

    private EvaluationRunEntity requireRun(UUID userId, UUID runId) {
        return evaluationRunRepository
                .findByIdAndUser_Id(runId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private EvaluationRunDetailDto toDetail(EvaluationRunEntity e, UUID userId) {
        UUID campaignId = e.getCampaign() != null ? e.getCampaign().getId() : null;
        boolean campaignMode = campaignId != null;
        List<EvaluationRunEntity> campaignChildren =
                campaignMode ? evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId) : List.of();
        int runItemCount = evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(e.getId()).size();
        int campaignItemCount = campaignMode ? countCampaignPersistedItems(campaignId, userId) : runItemCount;
        List<CampaignChildRunSummaryDto> childSummaries = buildCampaignChildSummaries(campaignChildren);
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
                e.getCompletedAt(),
                campaignId,
                campaignMode,
                labPresetAxisSupport.resolvePresetCode(e),
                resolveComparisonAxis(e),
                runItemCount,
                campaignItemCount,
                childSummaries);
    }

    private Map<String, Object> toDetailMap(EvaluationRunEntity e, UUID userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("status", e.getStatus().name());
        m.put("benchmarkKind", e.getBenchmarkKind());
        m.put("runKind", e.getRunKind());
        m.put("workflowSchemaVersion", e.getWorkflowSchemaVersion());
        m.put("datasetSha256", e.getDatasetSha256());
        m.put("datasetId", e.getDataset() != null ? e.getDataset().getId() : null);
        m.put("projectId", e.getProject() != null ? e.getProject().getId() : null);
        m.put("asyncTaskId", e.getAsyncTask() != null ? e.getAsyncTask().getId() : null);
        m.put("resolvedConfigSnapshotId", e.getResolvedConfigSnapshot() != null ? e.getResolvedConfigSnapshot().getId() : null);
        m.put("indexSnapshotId", e.getIndexSnapshot() != null ? e.getIndexSnapshot().getId() : null);
        m.put("indexSignatureHash", e.getIndexSignatureHash());
        m.put("presetId", e.getPreset() != null ? e.getPreset().getId() : null);
        m.put("llmModelId", e.getLlmModelId());
        m.put("embeddingModelId", e.getEmbeddingModelId());
        m.put("embeddingDimensions", e.getEmbeddingDimensions());
        m.put("classifierModelId", e.getClassifierModelId());
        m.put("aggregatesJson", e.getAggregatesJson());
        m.put("createdAt", e.getCreatedAt());
        m.put("completedAt", e.getCompletedAt());
        if (e.getCampaign() != null) {
            UUID campaignId = e.getCampaign().getId();
            m.put("campaignId", campaignId);
            m.put("campaignMode", true);
            m.put("presetKey", labPresetAxisSupport.resolvePresetCode(e));
            m.put("comparisonAxis", resolveComparisonAxis(e));
            m.put("persistedItemCount", evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(e.getId()).size());
            m.put("campaignPersistedItemCount", countCampaignPersistedItems(campaignId, userId));
            m.put(
                    "campaignChildRuns",
                    buildCampaignChildSummaries(
                                    evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId))
                            .stream()
                            .map(LabEvaluationRunService::childSummaryToMap)
                            .toList());
        }
        return m;
    }

    private List<EvaluationResultEntity> listPersistedItems(EvaluationRunEntity run, UUID userId) {
        if (run == null || run.getId() == null) {
            return List.of();
        }
        EvaluationCampaignEntity campaign = run.getCampaign();
        if (campaign == null || campaign.getId() == null) {
            return evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
        }
        List<UUID> childRunIds =
                evaluationRunRepository.findByCampaignIdAndUserId(campaign.getId(), userId).stream()
                        .map(EvaluationRunEntity::getId)
                        .filter(Objects::nonNull)
                        .toList();
        if (childRunIds.isEmpty()) {
            return evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(run.getId());
        }
        return evaluationResultRepository.findByRun_IdInOrderByEvaluatedAtAsc(childRunIds);
    }

    private int countCampaignPersistedItems(UUID campaignId, UUID userId) {
        if (campaignId == null || userId == null) {
            return 0;
        }
        return evaluationRunRepository.findByCampaignIdAndUserId(campaignId, userId).stream()
                .mapToInt(
                        r ->
                                evaluationResultRepository
                                        .findByRun_IdOrderByEvaluatedAtAsc(r.getId())
                                        .size())
                .sum();
    }

    private List<CampaignChildRunSummaryDto> buildCampaignChildSummaries(List<EvaluationRunEntity> children) {
        if (children == null || children.isEmpty()) {
            return List.of();
        }
        List<CampaignChildRunSummaryDto> out = new ArrayList<>();
        for (EvaluationRunEntity child : children) {
            if (child == null || child.getId() == null) {
                continue;
            }
            out.add(
                    new CampaignChildRunSummaryDto(
                            child.getId(),
                            labPresetAxisSupport.resolvePresetCode(child),
                            labPresetAxisSupport.resolvePresetLabel(child),
                            labPresetAxisSupport.comparisonLabel(child),
                            child.getLlmModelId(),
                            child.getStatus() != null ? child.getStatus().name() : null,
                            evaluationResultRepository.findByRun_IdOrderByEvaluatedAtAsc(child.getId()).size()));
        }
        return List.copyOf(out);
    }

    private static Map<String, Object> childSummaryToMap(CampaignChildRunSummaryDto dto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", dto.runId());
        m.put("presetKey", dto.presetKey());
        m.put("presetLabel", dto.presetLabel());
        m.put("comparisonLabel", dto.comparisonLabel());
        m.put("modelId", dto.modelId());
        m.put("status", dto.status());
        m.put("persistedItemCount", dto.persistedItemCount());
        return m;
    }

    private static String resolveComparisonAxis(EvaluationRunEntity run) {
        if (run.getAggregatesJson() != null) {
            Object axis = run.getAggregatesJson().get(LabPresetAxisSupport.AGG_KEY_COMPARISON_AXIS);
            if (axis != null && !String.valueOf(axis).isBlank()) {
                return String.valueOf(axis).trim();
            }
        }
        return LabPresetAxisSupport.isRagPresetCampaignRun(run)
                ? LabPresetAxisSupport.COMPARISON_AXIS_PRESET
                : null;
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
