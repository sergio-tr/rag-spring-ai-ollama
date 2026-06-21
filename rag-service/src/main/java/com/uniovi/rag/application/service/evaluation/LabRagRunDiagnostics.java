package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.service.evaluation.config.LabBenchmarkConfigPreflightResult;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * Safe, identifier-only diagnostics for Lab RAG preset benchmark orchestration.
 * Logs at DEBUG; validation failures throw {@link IllegalStateException} with a stable reason code.
 */
public final class LabRagRunDiagnostics {

    private static final String AGG_CORPUS_READINESS = "corpusReadiness";
    private static final String AGG_CONFIG_PREFLIGHT = "configPreflight";

    private LabRagRunDiagnostics() {}

    public static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (keyValues == null) {
            return out;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            out.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return out;
    }

    public static void logStage(Logger log, String stage, Map<String, ?> fields) {
        if (log == null || !log.isDebugEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder("lab_rag_diag stage=").append(stage);
        if (fields != null) {
            fields.forEach((k, v) -> sb.append(' ').append(k).append('=').append(safeValue(v)));
        }
        log.debug(sb.toString());
    }

    public static void logIncomingRequest(Logger log, UUID userId, StartBenchmarkRunRequest request) {
        if (request == null) {
            logStage(log, "incoming_request", Map.of("userId", userId, "reasonCode", "REQUEST_NULL"));
            return;
        }
        logStage(
                log,
                "incoming_request",
                fields(
                        "userId", userId,
                        "corpusId", request.corpusId(),
                        "datasetId", request.datasetId(),
                        "projectId", request.projectId(),
                        "presetKeys", request.experimentalPresetCodes(),
                        "resolvedConfigSnapshotId", request.resolvedConfigSnapshotId(),
                        "knowledgeIndexSnapshotId", request.indexSnapshotId(),
                        "autoReindex", request.autoReindexEffective(),
                        "bootstrapCorpus", request.bootstrapCorpusFromClasspathDocsEffective()));
    }

    public static void logRunAccepted(
            Logger log,
            UUID runId,
            UUID taskId,
            UUID corpusId,
            List<String> presetKeys,
            UUID resolvedConfigSnapshotId,
            UUID knowledgeIndexSnapshotId,
            EvaluationCorpusReadinessDto readiness) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("runId", runId);
        fields.put("taskId", taskId);
        fields.put("corpusId", corpusId);
        fields.put("presetKeys", presetKeys);
        fields.put("resolvedConfigSnapshotId", resolvedConfigSnapshotId);
        fields.put("knowledgeIndexSnapshotId", knowledgeIndexSnapshotId);
        if (readiness != null) {
            fields.put("documentCount", readiness.documentCount());
            fields.put("readyDocumentCount", readiness.readyCount());
            fields.put(
                    "indexStatus",
                    readiness.snapshotBlocker() != null
                            ? readiness.snapshotBlocker()
                            : (readiness.runnable() ? "RUNNABLE" : "NOT_RUNNABLE"));
            fields.put("reasonCode", readiness.primaryBlocker() != null ? readiness.primaryBlocker() : "OK");
        }
        logStage(log, "run_accepted", fields);
    }

    public static void logConfigPreflight(Logger log, UUID runId, LabBenchmarkConfigPreflightResult preflight) {
        if (preflight == null) {
            logStage(log, "config_preflight", Map.of("runId", runId, "reasonCode", LabRagReasonCodes.LAB_RAG_CONFIG_MISSING));
            return;
        }
        logStage(
                log,
                "config_preflight",
                fields(
                        "runId", runId,
                        "reasonCode", preflight.primaryCode() != null ? preflight.primaryCode() : "OK",
                        "presetKeys", preflight.requestedPresetCodes(),
                        "embeddingModelId", preflight.embeddingModelId()));
    }

    public static void logHandlerStart(
            Logger log,
            UUID taskId,
            UUID runId,
            UUID corpusId,
            UUID projectId,
            List<String> presetKeys,
            Map<String, Object> aggregatesJson) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("taskId", taskId);
        fields.put("runId", runId);
        fields.put("corpusId", corpusId);
        fields.put("projectId", projectId);
        fields.put("presetKeys", presetKeys);
        fields.put("hasCorpusReadinessAggregate", aggregatesJson != null && aggregatesJson.containsKey(AGG_CORPUS_READINESS));
        fields.put("hasConfigPreflightAggregate", aggregatesJson != null && aggregatesJson.containsKey(AGG_CONFIG_PREFLIGHT));
        logStage(log, "handler_start", fields);
    }

    public static void logPresetExecution(Logger log, UUID runId, String presetKey, int questionCount) {
        logStage(log, "preset_execution", fields("runId", runId, "presetKey", presetKey, "questionCount", questionCount));
    }

    public static void requireCorpusId(UUID corpusId) {
        if (corpusId == null) {
            fail(LabRagReasonCodes.LAB_RAG_CORPUS_MISSING);
        }
    }

    public static void requirePresetsResolvable(
            int resolvedPresetCount, int catalogPresetCount, List<String> rawRequestedCodes) {
        if (resolvedPresetCount > 0 || catalogPresetCount > 0) {
            return;
        }
        if (rawRequestedCodes != null && !rawRequestedCodes.isEmpty()) {
            fail(LabRagReasonCodes.LAB_RAG_PRESET_MISSING, "requested preset codes did not resolve");
        }
    }

    public static void requireDatasetItems(int questionCount) {
        if (questionCount <= 0) {
            fail(LabRagReasonCodes.LAB_RAG_DATASET_EMPTY);
        }
    }

    public static void requireConfigPreflightPresent(Map<String, Object> aggregatesJson) {
        Object raw = aggregatesJson != null ? aggregatesJson.get(AGG_CONFIG_PREFLIGHT) : null;
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return;
        }
        Object passed = map.get("passed");
        if (Boolean.FALSE.equals(passed)) {
            Object code = map.get("primaryCode");
            fail(
                    LabRagReasonCodes.LAB_RAG_CONFIG_MISSING,
                    code != null ? code.toString() : LabRagReasonCodes.LAB_RAG_CONFIG_MISSING);
        }
    }

    /**
     * Reads {@code corpusReadiness} from run aggregates for progress emission. Null optional blocker fields are omitted
     * (legacy rows and runnable corpora).
     */
    public static Map<String, Object> copyCorpusReadinessFromAggregates(
            Logger log, UUID runId, UUID taskId, Map<String, Object> aggregatesJson) {
        Map<String, Object> copied = LabCorpusReadinessAggregates.copyFromAggregates(aggregatesJson);
        if (log != null && log.isDebugEnabled() && aggregatesJson != null) {
            Object raw = aggregatesJson.get(AGG_CORPUS_READINESS);
            if (raw instanceof Map<?, ?> map) {
                List<String> omittedNullKeys = new ArrayList<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null && e.getValue() == null) {
                        omittedNullKeys.add(String.valueOf(e.getKey()));
                    }
                }
                if (!omittedNullKeys.isEmpty()) {
                    logStage(
                            log,
                            "corpus_readiness_normalized",
                            fields(
                                    "runId", runId,
                                    "taskId", taskId,
                                    "omittedNullFieldKeys", omittedNullKeys,
                                    "documentCount", copied.get("documentCount"),
                                    "readyDocumentCount", copied.get("readyCount")));
                }
            }
        }
        return copied;
    }

    public static void fail(String reasonCode) {
        throw new IllegalStateException(reasonCode);
    }

    public static void fail(String reasonCode, String detail) {
        if (detail == null || detail.isBlank() || detail.equals(reasonCode)) {
            throw new IllegalStateException(reasonCode);
        }
        throw new IllegalStateException(reasonCode + ": " + detail);
    }

    private static String safeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        return String.valueOf(value);
    }
}
