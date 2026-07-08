package com.uniovi.rag.application.service.evaluation.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpRollupCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpSchema;
import com.uniovi.rag.application.service.evaluation.metrics.RagPresetRetrievalExportSupport;
import com.uniovi.rag.application.service.evaluation.metrics.ScoreExportSupport;
import com.uniovi.rag.application.service.evaluation.provenance.EvaluationBuildMetadata;
import com.uniovi.rag.application.service.evaluation.provenance.EvaluationProvenanceKeys;
import com.uniovi.rag.application.service.evaluation.provenance.EvaluationProvenanceSupport;
import com.uniovi.rag.infrastructure.config.PromptBundleFingerprint;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds evaluation export contract v1 without recomputing scores. */
public final class EvaluationExportV1Builder {

    private EvaluationExportV1Builder() {}

    public static Map<String, Object> buildResultsJson(EvaluationRunEntity run, List<EvaluationResultEntity> items) {
        String exportedAt = Instant.now().toString();
        Map<String, Object> manifest = buildEvaluationManifest(run, items, exportedAt);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportSchemaVersion", EvaluationExportV1Schema.VERSION);
        root.put("exportedAt", exportedAt);
        root.put("mvpSchemaVersion", BenchmarkMvpSchema.VERSION);
        root.put("run", buildRunSection(run));
        root.put("provenance", buildProvenanceSection(run));
        root.put("provider", buildProviderSection(run, items));
        root.put("model", buildModelSection(run));
        root.put("manifest", manifest);
        root.put("metrics", BenchmarkMvpRollupCalculator.build(items, run));
        root.put("results", buildResultRows(run, items));
        return root;
    }

    public static String buildSummaryCsv(EvaluationRunEntity run, List<EvaluationResultEntity> items) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", EvaluationExportV1Schema.SUMMARY_CSV_COLUMNS)).append('\n');
        for (EvaluationResultEntity item : items) {
            Map<String, String> row = buildSummaryCsvRow(item, run);
            List<String> cells =
                    EvaluationExportV1Schema.SUMMARY_CSV_COLUMNS.stream()
                            .map(h -> csvEscape(row.getOrDefault(h, "")))
                            .toList();
            sb.append(String.join(",", cells)).append('\n');
        }
        return sb.toString();
    }

    public static byte[] buildFullBundleZip(
            EvaluationRunEntity run,
            List<EvaluationResultEntity> items,
            String legacyMvpItemsJson,
            String legacyMvpItemsCsv,
            String legacyMvpRollupsJson,
            ObjectMapper objectMapper)
            throws JsonProcessingException {
        Map<String, Object> results = buildResultsJson(run, items);
        byte[] resultsJson = objectMapper.writeValueAsBytes(results);
        byte[] summaryCsv = buildSummaryCsv(run, items).getBytes(StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) results.get("manifest");
        byte[] manifestJson = objectMapper.writeValueAsBytes(manifest);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            putEntry(zos, EvaluationExportV1Schema.RESULTS_JSON, resultsJson);
            putEntry(zos, EvaluationExportV1Schema.SUMMARY_CSV, summaryCsv);
            putEntry(zos, EvaluationExportV1Schema.EVALUATION_MANIFEST_JSON, manifestJson);
            if (legacyMvpItemsJson != null) {
                putEntry(zos, "mvp/items.json", legacyMvpItemsJson.getBytes(StandardCharsets.UTF_8));
            }
            if (legacyMvpItemsCsv != null) {
                putEntry(zos, "mvp/items.csv", legacyMvpItemsCsv.getBytes(StandardCharsets.UTF_8));
            }
            if (legacyMvpRollupsJson != null) {
                putEntry(zos, "mvp/rollups.json", legacyMvpRollupsJson.getBytes(StandardCharsets.UTF_8));
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build evaluation full bundle zip", e);
        }
    }

    static Map<String, Object> buildEvaluationManifest(
            EvaluationRunEntity run, List<EvaluationResultEntity> items, String exportedAt) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestSchemaVersion", EvaluationExportV1Schema.MANIFEST_SCHEMA_VERSION);
        manifest.put("exportSchemaVersion", EvaluationExportV1Schema.VERSION);
        manifest.put("exportedAt", exportedAt);
        manifest.put("identity", buildIdentitySection(run, items));
        manifest.put("bindings", buildBindingsSection(run));
        manifest.put("reproducibility", buildProvenanceSection(run));
        manifest.put("experimentalSnapshots", buildExperimentalSnapshotsSection(run));
        return Map.copyOf(manifest);
    }

    private static List<Map<String, Object>> buildResultRows(EvaluationRunEntity run, List<EvaluationResultEntity> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvaluationResultEntity item : items) {
            EvaluationRunEntity itemRun = item.getRun() != null ? item.getRun() : run;
            Map<String, Object> metricsPayload =
                    item.getMetricsPayload() != null ? item.getMetricsPayload() : Map.of();
            Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(item, itemRun);
            @SuppressWarnings("unchecked")
            Map<String, Object> operational = (Map<String, Object>) mvp.getOrDefault("operational", Map.of());
            String outcome = str(operational.get("outcome"));
            if (outcome.isBlank()) {
                outcome = "EXECUTED";
            }

            Map<String, String> flat = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(item, itemRun);
            List<Map<String, String>> sources =
                    RagPresetRetrievalExportSupport.enrichSourceEntries(sourcesFromMetrics(metricsPayload, flat), metricsPayload);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", item.getId());
            row.put("datasetQuestionId", firstNonBlank(str(mvp.get("datasetQuestionId")), metricStr(metricsPayload, "dataset_question_id")));
            row.put("question", nullToEmpty(item.getQuestionText()));
            row.put("expectedAnswer", nullToEmpty(item.getExpectedAnswer()));
            row.put(
                    "answer",
                    Map.of(
                            "text", nullToEmpty(item.getActualAnswer()),
                            "queryType", nullToEmpty(item.getQueryType())));
            row.put("metrics", mvp);
            row.put("sources", sources);
            row.put("traceIds", traceIds(metricsPayload));
            EvaluationDerivedErrorClassifier.classify(outcome, metricsPayload, operational)
                    .ifPresent(c -> row.put("derivedErrorClass", c));

            Map<String, Object> technical = new LinkedHashMap<>();
            technical.put("metricsPayload", metricsPayload);
            technical.put("mvp", mvp);
            technical.put("correctness", item.getCorrectness());
            technical.put("benchmarkKind", item.getBenchmarkKind());
            technical.put("evaluatedAt", item.getEvaluatedAt());
            technical.put("latencyMs", item.getLatencyMs());
            RagPresetRetrievalExportSupport.putJsonExportFields(technical, metricsPayload);
            row.put("technical", technical);

            // Legacy-friendly aliases for existing UI adapters.
            row.put("questionText", item.getQuestionText());
            row.put("actualAnswer", item.getActualAnswer());
            row.put("mvp", mvp);
            row.put("metricsPayload", metricsPayload);
            out.add(row);
        }
        return List.copyOf(out);
    }

    private static Map<String, String> buildSummaryCsvRow(EvaluationResultEntity item, EvaluationRunEntity run) {
        EvaluationRunEntity itemRun = item.getRun() != null ? item.getRun() : run;
        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(item, itemRun);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.getOrDefault("retrieval", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.getOrDefault("generation", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.getOrDefault("operational", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) mvp.getOrDefault("analysis", Map.of());
        Map<String, Object> mp = item.getMetricsPayload() != null ? item.getMetricsPayload() : Map.of();

        Map<String, String> row = new LinkedHashMap<>();
        row.put("itemId", uuid(item.getId()));
        row.put("evaluationRunId", uuid(itemRun != null ? itemRun.getId() : null));
        row.put(
                "datasetQuestionId",
                firstNonBlank(str(mvp.get("datasetQuestionId")), metricStr(mp, BenchmarkResultRowKeys.DATASET_QUESTION_ID)));
        row.put("question", nullToEmpty(item.getQuestionText()));
        row.put("outcome", firstNonBlank(str(op.get("outcome")), "EXECUTED"));
        row.put("correctness", csvVal(item.getCorrectness()));
        row.put("finalScore", ScoreExportSupport.formatFinalScoreForCsv(analysis));
        row.put("llmJudgeScore", csvVal(gen.get("llmJudgeScore")));
        row.put("normalizedExactMatch", csvVal(gen.get("normalizedExactMatch")));
        row.put("recallAt1", csvVal(ret.get("recallAt1")));
        row.put("latencyMs", csvVal(op.get("latencyMs")));
        row.put("llmModelId", firstNonBlank(str(op.get("modelId")), itemRun != null ? nullToEmpty(itemRun.getLlmModelId()) : ""));
        row.put("presetCode", csvVal(op.get("presetCode")));
        row.put("queryType", firstNonBlank(str(mvp.get("queryType")), nullToEmpty(item.getQueryType())));
        EvaluationDerivedErrorClassifier.classify(row.get("outcome"), mp, op)
                .ifPresent(c -> row.put("derivedErrorClass", c));
        row.put("traceId", traceIds(mp).getOrDefault("traceId", ""));
        row.put("benchmarkKind", nullToEmpty(item.getBenchmarkKind()));
        row.put("finalScoreAvailable", String.valueOf(ScoreExportSupport.isFinalScoreAvailable(analysis)));
        row.put("answerability", csvVal(analysis.get("answerability")));
        row.put("sourceCount", csvVal(mp.get("sourceCount")));
        return row;
    }

    private static Map<String, Object> buildRunSection(EvaluationRunEntity run) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (run == null) {
            return m;
        }
        m.put("id", run.getId());
        m.put("name", run.getName());
        m.put("status", run.getStatus() != null ? run.getStatus().name() : null);
        m.put("benchmarkKind", run.getBenchmarkKind());
        m.put("runKind", run.getRunKind());
        m.put("workflowSchemaVersion", run.getWorkflowSchemaVersion());
        m.put("datasetSha256", run.getDatasetSha256());
        m.put("datasetId", run.getDataset() != null ? run.getDataset().getId() : null);
        m.put("corpusId", run.getEvaluationCorpus() != null ? run.getEvaluationCorpus().getId() : null);
        m.put("projectId", run.getProject() != null ? run.getProject().getId() : null);
        m.put("asyncTaskId", run.getAsyncTask() != null ? run.getAsyncTask().getId() : null);
        m.put(
                "resolvedConfigSnapshotId",
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getId() : null);
        m.put(
                "resolvedConfigHash",
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getConfigHash() : null);
        m.put("indexSnapshotId", run.getIndexSnapshot() != null ? run.getIndexSnapshot().getId() : null);
        m.put("indexSignatureHash", resolveIndexSignatureHash(run));
        m.put("indexProfileHash", run.getIndexSnapshot() != null ? run.getIndexSnapshot().getIndexProfileHash() : null);
        m.put("presetId", run.getPreset() != null ? run.getPreset().getId() : null);
        m.put("presetName", run.getPreset() != null ? run.getPreset().getName() : null);
        Object presetCodes = aggregateValue(run, "requestedPresetCodes", "experimentalPresetCodes");
        if (presetCodes != null) {
            m.put("presetCodes", presetCodes);
        }
        m.put("aggregatesJson", run.getAggregatesJson());
        m.put("createdAt", run.getCreatedAt());
        m.put("completedAt", run.getCompletedAt());
        if (run.getCampaign() != null) {
            m.put("campaignId", run.getCampaign().getId());
            m.put("campaignMode", true);
        }
        return m;
    }

    private static Map<String, Object> buildModelSection(EvaluationRunEntity run) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (run == null) {
            return m;
        }
        m.put("llmModelId", run.getLlmModelId());
        m.put("embeddingModelId", run.getEmbeddingModelId());
        m.put("embeddingDimensions", run.getEmbeddingDimensions());
        m.put("classifierModelId", run.getClassifierModelId());
        return m;
    }

    private static Map<String, Object> buildProvenanceSection(EvaluationRunEntity run) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (run == null) {
            return EvaluationProvenanceSupport.withExportDefaults(m);
        }
        Map<String, Object> persisted = EvaluationProvenanceSupport.readFromRun(run);
        if (!persisted.isEmpty()) {
            m.putAll(persisted);
        }
        if (run.getPromptProfileSnapshot() != null) {
            Map<String, Object> prompt = run.getPromptProfileSnapshot();
            putIfNonBlank(m, EvaluationProvenanceKeys.PROMPT_PROFILE_VERSION, prompt.get("profileVersion"));
            putIfNonBlank(
                    m,
                    EvaluationProvenanceKeys.EFFECTIVE_SYSTEM_PROMPT_SHA256,
                    prompt.get("effectiveSystemPromptSha256"));
        }
        ensurePromptBundleProvenance(m);
        return EvaluationProvenanceSupport.withExportDefaults(m);
    }

    private static void ensurePromptBundleProvenance(Map<String, Object> target) {
        if (EvaluationProvenanceSupport.stringValue(target, EvaluationProvenanceKeys.PROMPT_BUNDLE_SHA256)
                .isBlank()) {
            target.putAll(PromptBundleFingerprint.computeFrozen().toProvenanceMap());
        }
    }

    private static void putIfNonBlank(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value).trim();
        if (!s.isEmpty()) {
            target.putIfAbsent(key, s);
        }
    }

    private static Map<String, Object> buildProviderSection(EvaluationRunEntity run, List<EvaluationResultEntity> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> provenance = run != null ? EvaluationProvenanceSupport.readFromRun(run) : Map.of();
        String chatProvider =
                firstNonBlank(
                        findInItems(items, "chatProvider", "chat_provider", "llmProvider", "llm_provider"),
                        EvaluationProvenanceSupport.stringValue(provenance, EvaluationProvenanceKeys.CHAT_PROVIDER));
        String embeddingProvider =
                firstNonBlank(
                        findInItems(items, "embeddingProvider", "embedding_provider"),
                        EvaluationProvenanceSupport.stringValue(
                                provenance, EvaluationProvenanceKeys.EMBEDDING_PROVIDER));
        m.put("chatProvider", chatProvider.isBlank() ? EvaluationBuildMetadata.UNKNOWN : chatProvider);
        m.put("embeddingProvider", embeddingProvider.isBlank() ? EvaluationBuildMetadata.UNKNOWN : embeddingProvider);
        if (run != null) {
            m.put("chatModel", nullToEmpty(run.getLlmModelId()));
            m.put("embeddingModel", nullToEmpty(run.getEmbeddingModelId()));
        }
        if (run != null && run.getLlmExperimentalSnapshot() != null) {
            m.put("llmExperimentalSnapshot", run.getLlmExperimentalSnapshot());
        }
        if (run != null && run.getEmbeddingExperimentalSnapshot() != null) {
            m.put("embeddingExperimentalSnapshot", run.getEmbeddingExperimentalSnapshot());
        }
        return m;
    }

    private static Map<String, Object> buildIdentitySection(EvaluationRunEntity run, List<EvaluationResultEntity> items) {
        Map<String, Object> provider = buildProviderSection(run, items);
        Map<String, Object> model = buildModelSection(run);
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("chatProvider", provider.get("chatProvider"));
        identity.put("embeddingProvider", provider.get("embeddingProvider"));
        identity.put("chatModel", provider.get("chatModel"));
        identity.put("embeddingModel", provider.get("embeddingModel"));
        identity.put("classifierModelId", model.get("classifierModelId"));
        return identity;
    }

    private static Map<String, Object> buildBindingsSection(EvaluationRunEntity run) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (run == null) {
            return m;
        }
        m.put("datasetId", run.getDataset() != null ? run.getDataset().getId() : null);
        m.put("datasetSha256", run.getDatasetSha256());
        m.put("corpusId", run.getEvaluationCorpus() != null ? run.getEvaluationCorpus().getId() : null);
        m.put(
                "resolvedConfigSnapshotId",
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getId() : null);
        m.put(
                "resolvedConfigHash",
                run.getResolvedConfigSnapshot() != null ? run.getResolvedConfigSnapshot().getConfigHash() : null);
        m.put("indexSnapshotId", run.getIndexSnapshot() != null ? run.getIndexSnapshot().getId() : null);
        m.put("indexSignatureHash", resolveIndexSignatureHash(run));
        m.put("indexProfileHash", run.getIndexSnapshot() != null ? run.getIndexSnapshot().getIndexProfileHash() : null);
        m.put("presetId", run.getPreset() != null ? run.getPreset().getId() : null);
        m.put("presetName", run.getPreset() != null ? run.getPreset().getName() : null);
        Object presetCodes = aggregateValue(run, "requestedPresetCodes", "experimentalPresetCodes");
        if (presetCodes != null) {
            m.put("presetCodes", presetCodes);
        }
        return m;
    }

    private static Map<String, Object> buildExperimentalSnapshotsSection(EvaluationRunEntity run) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (run == null) {
            return m;
        }
        if (run.getLlmExperimentalSnapshot() != null) {
            m.put("llm", run.getLlmExperimentalSnapshot());
        }
        if (run.getEmbeddingExperimentalSnapshot() != null) {
            m.put("embedding", run.getEmbeddingExperimentalSnapshot());
        }
        if (run.getPromptProfileSnapshot() != null) {
            m.put("promptProfile", run.getPromptProfileSnapshot());
        }
        m.put("promptBundle", PromptBundleFingerprint.computeFrozen().toProvenanceMap());
        return m;
    }

    private static String resolveIndexSignatureHash(EvaluationRunEntity run) {
        if (run == null) {
            return "";
        }
        String fromRun = run.getIndexSignatureHash();
        if (fromRun != null && !fromRun.isBlank()) {
            return fromRun.trim();
        }
        if (run.getIndexSnapshot() != null && run.getIndexSnapshot().getSignatureHash() != null) {
            return run.getIndexSnapshot().getSignatureHash().trim();
        }
        return "";
    }

    private static Object aggregateValue(EvaluationRunEntity run, String... keys) {
        if (run == null || run.getAggregatesJson() == null) {
            return null;
        }
        for (String key : keys) {
            Object value = run.getAggregatesJson().get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String findInItems(List<EvaluationResultEntity> items, String... keys) {
        if (items == null) {
            return "";
        }
        for (EvaluationResultEntity item : items) {
            Map<String, Object> mp = item.getMetricsPayload();
            if (mp == null) {
                continue;
            }
            for (String key : keys) {
                String v = metricStr(mp, key);
                if (!v.isBlank()) {
                    return v;
                }
            }
        }
        return "";
    }

    private static Map<String, String> traceIds(Map<String, Object> metricsPayload) {
        Map<String, String> out = new LinkedHashMap<>();
        putIfPresent(out, "traceId", metricsPayload, "traceId", "trace_id", "runtimeTraceId");
        return out;
    }

    private static void putIfPresent(Map<String, String> target, String key, Map<String, Object> mp, String... keys) {
        for (String k : keys) {
            String v = metricStr(mp, k);
            if (!v.isBlank()) {
                target.put(key, v);
                return;
            }
        }
    }

    private static List<Map<String, String>> sourcesFromMetrics(Map<String, Object> mp, Map<String, String> flat) {
        List<Map<String, String>> out = new ArrayList<>();
        Object rawSources = mp.get("sources");
        if (rawSources instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> sm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> src = (Map<String, Object>) sm;
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("filename", firstNonBlank(str(src.get("filename")), str(src.get("fileName"))));
                    entry.put("documentId", str(src.get("documentId")));
                    entry.put("snippet", str(src.get("snippet")));
                    if (!entry.get("filename").isBlank() || !entry.get("documentId").isBlank()) {
                        out.add(Map.copyOf(entry));
                    }
                }
            }
            if (!out.isEmpty()) {
                return List.copyOf(out);
            }
        }
        String docIds = firstNonBlank(flat.get("retrievedDocumentIds"), joinIds(mp.get("retrieved_document_ids")));
        if (!docIds.isBlank()) {
            for (String id : docIds.split(";")) {
                String t = id.trim();
                if (!t.isEmpty()) {
                    out.add(Map.of("documentId", t, "filename", "", "snippet", ""));
                }
            }
        }
        return List.copyOf(out);
    }

    private static void putEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static String joinIds(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).reduce((a, b) -> a + ";" + b).orElse("");
        }
        return raw != null ? String.valueOf(raw) : "";
    }

    private static String csvVal(Object raw) {
        if (raw == null) {
            return "";
        }
        return String.valueOf(raw);
    }

    private static String metricStr(Map<String, Object> mp, String key) {
        if (mp == null || key == null || !mp.containsKey(key)) {
            return "";
        }
        Object v = mp.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String uuid(UUID id) {
        return id != null ? id.toString() : "";
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o).trim() : "";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
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
}
