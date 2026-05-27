package com.uniovi.rag.infrastructure.persistence.evaluation;

import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.metrics.LabBenchmarkExportLabels;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Human-readable campaign export rows for thesis evidence (JSON/CSV labels).
 * Technical MVP metrics remain available under {@code mvp} / flat CSV columns.
 */
public final class LabCampaignHumanExportBuilder {

    private static final String COMPARISON_AXIS_PRESET = "PRESET_CODE";
    private static final String AGG_KEY_REQUESTED_PRESET_CODES = "requested_preset_codes";

    public static final String EXPORT_KIND_ITEMS = "campaign-items";
    public static final String EXPORT_KIND_SUMMARY = "campaign-summary";

    private LabCampaignHumanExportBuilder() {}

    public static Map<String, Object> campaignHeader(
            EvaluationCampaignEntity campaign,
            String campaignType,
            String comparisonAxis,
            boolean comparativeMode,
            List<EvaluationRunEntity> runs) {
        EvaluationRunEntity anchor = runs != null && !runs.isEmpty() ? runs.getFirst() : null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("campaignId", campaign.getId());
        out.put("campaignName", campaign.getName());
        out.put("campaignType", campaignType);
        out.put("comparisonAxis", comparisonAxis);
        out.put("comparisonAxisLabel", LabBenchmarkExportLabels.comparisonAxisLabel(resolveBenchmarkKind(runs)));
        out.put("comparativeMode", comparativeMode);
        out.put("studyType", campaign.getStudyType());
        out.put("knowledgeBaseId", knowledgeBaseId(anchor));
        out.put("knowledgeBaseName", knowledgeBaseName(anchor));
        out.put("datasetId", datasetId(anchor));
        out.put("datasetName", datasetName(anchor));
        out.put("runCount", runs != null ? runs.size() : 0);
        return out;
    }

    public static Map<String, Object> buildHumanItemRow(
            UUID campaignId,
            String campaignType,
            String comparisonAxis,
            EvaluationRunEntity run,
            EvaluationResultEntity item) {
        Map<String, Object> mp = item.getMetricsPayload() != null ? item.getMetricsPayload() : Map.of();
        Map<String, String> flat = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(item, run);
        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(item, run);
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.getOrDefault("operational", Map.of());

        String presetCode = firstNonBlank(str(op.get("presetCode")), flat.get("presetCode"));
        presetCode = LabBenchmarkExportLabels.normalizeGroupKey(presetCode);
        String presetLabel = firstNonBlank(flat.get("presetLabel"), humanPresetLabel(run), presetCode);
        if (LabBenchmarkExportLabels.MISSING_METADATA.equals(presetCode)) {
            presetCode = "";
            presetLabel = "";
        }

        String outcome = firstNonBlank(str(op.get("outcome")), "EXECUTED");
        String failureReason =
                firstNonBlank(
                        str(op.get("unsupportedReason")),
                        str(op.get("skipReason")),
                        str(op.get("failureCode")));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("campaignId", campaignId);
        row.put("campaignType", campaignType);
        row.put("comparisonAxis", comparisonAxis);
        row.put("itemId", item.getId());
        row.put("runId", run.getId());
        row.put("runName", run.getName());
        row.put("knowledgeBaseId", knowledgeBaseId(run));
        row.put("knowledgeBaseName", knowledgeBaseName(run));
        row.put("datasetId", datasetId(run));
        row.put("datasetName", datasetName(run));
        row.put("presetCode", presetCode);
        row.put("presetLabel", presetLabel);
        row.put("modelLabel", humanModelLabel(run));
        row.put("snapshotId", firstNonBlank(flat.get("indexSnapshotId"), flat.get("effectiveGroupSnapshotId")));
        row.put("documentName", documentNameHint(flat));
        row.put("question", nullToEmpty(item.getQuestionText()));
        row.put("expectedAnswer", nullToEmpty(item.getExpectedAnswer()));
        row.put("answer", nullToEmpty(item.getActualAnswer()));
        row.put("sources", sourcesFromMetrics(mp, flat));
        row.put("status", outcome);
        row.put("failureReason", failureReason);
        row.put("metrics", humanMetricsSummary(mvp));
        row.put("evaluatedAt", item.getEvaluatedAt());
        row.put("mvp", mvp);
        return row;
    }

    public static Map<String, Object> buildSummaryRow(
            Map<String, Object> comparisonRow,
            EvaluationRunEntity run,
            UUID campaignId,
            String campaignType,
            String comparisonAxis,
            boolean comparativeMode) {
        Map<String, Object> out = new LinkedHashMap<>(comparisonRow);
        out.put("campaignId", campaignId);
        out.put("campaignType", campaignType);
        out.put("comparisonAxis", comparisonAxis);
        out.put("comparativeMode", comparativeMode);
        out.put("knowledgeBaseId", knowledgeBaseId(run));
        out.put("knowledgeBaseName", knowledgeBaseName(run));
        out.put("datasetId", datasetId(run));
        out.put("datasetName", datasetName(run));
        String axis = str(comparisonRow.get("axisValue"));
        if (!axis.isBlank()) {
            out.put(
                    "comparisonLabel",
                    LabBenchmarkExportLabels.displayGroupValue(
                            comparisonAxis.equals(COMPARISON_AXIS_PRESET) ? "presetCode" : "modelId",
                            axis));
        }
        return out;
    }

    private static Map<String, Object> humanMetricsSummary(Map<String, Object> mvp) {
        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.getOrDefault("generation", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.getOrDefault("retrieval", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.getOrDefault("operational", Map.of());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("correctness", gen.get("correctness"));
        m.put("llmJudgeScore", gen.get("llmJudgeScore"));
        m.put("recallAt1", ret.get("recallAt1"));
        m.put("latencyMs", op.get("latencyMs"));
        return m;
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
                        out.add(entry);
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

    private static String documentNameHint(Map<String, String> flat) {
        String ids = firstNonBlank(flat.get("retrievedDocumentIds"), flat.get("goldDocumentIds"));
        return ids;
    }

    private static UUID knowledgeBaseId(EvaluationRunEntity run) {
        EvaluationCorpusEntity corpus = run != null ? run.getEvaluationCorpus() : null;
        return corpus != null ? corpus.getId() : null;
    }

    private static String knowledgeBaseName(EvaluationRunEntity run) {
        EvaluationCorpusEntity corpus = run != null ? run.getEvaluationCorpus() : null;
        if (corpus != null && corpus.getName() != null && !corpus.getName().isBlank()) {
            return corpus.getName().trim();
        }
        return "";
    }

    private static UUID datasetId(EvaluationRunEntity run) {
        EvaluationDatasetEntity ds = run != null ? run.getDataset() : null;
        return ds != null ? ds.getId() : null;
    }

    private static String datasetName(EvaluationRunEntity run) {
        EvaluationDatasetEntity ds = run != null ? run.getDataset() : null;
        if (ds != null && ds.getName() != null && !ds.getName().isBlank()) {
            return ds.getName().trim();
        }
        return "";
    }

    private static String humanModelLabel(EvaluationRunEntity run) {
        if (run == null) {
            return "";
        }
        if (run.getLlmModelId() != null && !run.getLlmModelId().isBlank()) {
            return run.getLlmModelId().trim();
        }
        if (run.getEmbeddingModelId() != null && !run.getEmbeddingModelId().isBlank()) {
            return run.getEmbeddingModelId().trim();
        }
        return "";
    }

    private static String humanPresetLabel(EvaluationRunEntity run) {
        if (run == null || run.getAggregatesJson() == null) {
            return "";
        }
        Object codes = run.getAggregatesJson().get(AGG_KEY_REQUESTED_PRESET_CODES);
        if (codes instanceof List<?> list && !list.isEmpty() && list.getFirst() != null) {
            return list.getFirst().toString();
        }
        return "";
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

    private static String joinIds(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).reduce((a, b) -> a + ";" + b).orElse("");
        }
        return raw != null ? String.valueOf(raw) : "";
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
