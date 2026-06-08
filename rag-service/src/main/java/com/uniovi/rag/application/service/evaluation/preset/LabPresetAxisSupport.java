package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.service.evaluation.BenchmarkRunOrchestrator;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves RAG preset comparison axis values and labels for campaign child runs. */
@Component
public class LabPresetAxisSupport {

    public static final String AGG_KEY_PRESET_KEY = "presetKey";
    public static final String AGG_KEY_PRESET_LABEL = "presetLabel";
    public static final String AGG_KEY_COMPARISON_AXIS = "comparisonAxis";
    public static final String COMPARISON_AXIS_PRESET = "PRESET_CODE";

    private final EvaluationReferenceBundleLoader referenceBundleLoader;

    public LabPresetAxisSupport(EvaluationReferenceBundleLoader referenceBundleLoader) {
        this.referenceBundleLoader = referenceBundleLoader;
    }

    public String catalogLabel(String presetCode) {
        if (presetCode == null || presetCode.isBlank()) {
            return "";
        }
        Optional<RagExperimentalPresetCode> parsed = RagExperimentalPresetCode.tryParse(presetCode.trim());
        if (parsed.isEmpty()) {
            return presetCode.trim();
        }
        for (RagPresetDefinition def : referenceBundleLoader.getSnapshot().workbook().ragPresetCatalog()) {
            if (def != null && def.presetId() == parsed.get()) {
                String name = def.name();
                return name != null && !name.isBlank() ? name.trim() : presetCode.trim();
            }
        }
        return presetCode.trim();
    }

    public String resolvePresetCode(EvaluationRunEntity run) {
        if (run == null) {
            return "";
        }
        Map<String, Object> agg = run.getAggregatesJson();
        if (agg != null) {
            String fromKey = readSingleString(agg.get(AGG_KEY_PRESET_KEY));
            if (!fromKey.isBlank()) {
                return fromKey;
            }
            String fromRequested = readFirstListEntry(agg.get(BenchmarkRunOrchestrator.AGG_KEY_REQUESTED_PRESET_CODES));
            if (!fromRequested.isBlank()) {
                return fromRequested;
            }
            String fromRunPlan = readFirstListEntry(readMap(agg.get("runPlan")).get("requestedPresetCodes"));
            if (!fromRunPlan.isBlank()) {
                return fromRunPlan;
            }
        }
        return parsePresetFromRunName(run.getName());
    }

    public String resolvePresetLabel(EvaluationRunEntity run) {
        if (run == null) {
            return "";
        }
        Map<String, Object> agg = run.getAggregatesJson();
        if (agg != null) {
            String stored = readSingleString(agg.get(AGG_KEY_PRESET_LABEL));
            if (!stored.isBlank()) {
                return stored;
            }
        }
        String code = resolvePresetCode(run);
        return code.isBlank() ? "" : catalogLabel(code);
    }

    public String comparisonLabel(EvaluationRunEntity run) {
        String code = resolvePresetCode(run);
        if (code.isBlank()) {
            return "";
        }
        String label = resolvePresetLabel(run);
        if (label.isBlank() || label.equalsIgnoreCase(code)) {
            return code;
        }
        return code + " — " + label;
    }

    public Map<String, Object> ragPresetChildAggregates(String presetCode) {
        String code = presetCode != null ? presetCode.trim() : "";
        String label = catalogLabel(code);
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put(BenchmarkRunOrchestrator.AGG_KEY_REQUESTED_PRESET_CODES, List.of(code));
        agg.put(AGG_KEY_PRESET_KEY, code);
        agg.put(AGG_KEY_PRESET_LABEL, label);
        agg.put(AGG_KEY_COMPARISON_AXIS, COMPARISON_AXIS_PRESET);
        return agg;
    }

    public void mergeRagPresetChildAggregates(EvaluationRunEntity run, String presetCode) {
        if (run == null) {
            return;
        }
        Map<String, Object> agg = new LinkedHashMap<>();
        if (run.getAggregatesJson() != null && !run.getAggregatesJson().isEmpty()) {
            agg.putAll(run.getAggregatesJson());
        }
        agg.putAll(ragPresetChildAggregates(presetCode));
        run.setAggregatesJson(Map.copyOf(agg));
    }

    /** Persists campaign child-run comparison and linkage metadata for preset-axis exports. */
    public void enrichRagPresetChildRun(EvaluationRunEntity run, UUID campaignId, String presetCode) {
        mergeRagPresetChildAggregates(run, presetCode);
        if (run == null) {
            return;
        }
        Map<String, Object> agg = new LinkedHashMap<>(run.getAggregatesJson() != null ? run.getAggregatesJson() : Map.of());
        if (campaignId != null) {
            agg.put("campaignId", campaignId.toString());
        }
        if (run.getBenchmarkKind() != null && !run.getBenchmarkKind().isBlank()) {
            agg.put("benchmarkKind", run.getBenchmarkKind().trim());
        }
        if (run.getLlmModelId() != null && !run.getLlmModelId().isBlank()) {
            agg.put("modelId", run.getLlmModelId().trim());
        }
        if (run.getEvaluationCorpus() != null && run.getEvaluationCorpus().getId() != null) {
            agg.put("corpusId", run.getEvaluationCorpus().getId().toString());
        }
        if (run.getIndexSnapshot() != null && run.getIndexSnapshot().getId() != null) {
            agg.put("snapshotId", run.getIndexSnapshot().getId().toString());
        }
        if (run.getResolvedConfigSnapshot() != null && run.getResolvedConfigSnapshot().getId() != null) {
            agg.put("resolvedConfigSnapshotId", run.getResolvedConfigSnapshot().getId().toString());
        }
        run.setAggregatesJson(Map.copyOf(agg));
    }

    public static String formatComparisonLabel(String presetCode, String presetLabel) {
        String code = presetCode != null ? presetCode.trim() : "";
        if (code.isEmpty()) {
            return "";
        }
        String label = presetLabel != null ? presetLabel.trim() : "";
        if (label.isEmpty() || label.equalsIgnoreCase(code)) {
            return code;
        }
        return code + " — " + label;
    }

    public static String parsePresetFromRunName(String runName) {
        if (runName == null || runName.isBlank()) {
            return "";
        }
        String marker = "preset ";
        int idx = runName.lastIndexOf(marker);
        if (idx < 0) {
            return "";
        }
        String tail = runName.substring(idx + marker.length()).trim();
        int space = tail.indexOf(' ');
        return (space > 0 ? tail.substring(0, space) : tail).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMap(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String readSingleString(Object raw) {
        if (raw == null) {
            return "";
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? "" : s;
    }

    private static String readFirstListEntry(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        Object first = list.getFirst();
        return first != null ? String.valueOf(first).trim() : "";
    }

    public static boolean isRagPresetCampaignRun(EvaluationRunEntity run) {
        return run != null
                && BenchmarkKind.RAG_PRESET_END_TO_END.name().equals(run.getBenchmarkKind())
                && run.getCampaign() != null;
    }
}
