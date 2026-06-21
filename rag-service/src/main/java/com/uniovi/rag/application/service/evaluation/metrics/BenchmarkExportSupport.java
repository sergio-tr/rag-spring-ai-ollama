package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetBenchmarkGate;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Export-time support status and preset metadata without mutating persisted metrics. */
public final class BenchmarkExportSupport {

    private static final Pattern PRESET_ORDER = Pattern.compile("^P(\\d+)$", Pattern.CASE_INSENSITIVE);

    private BenchmarkExportSupport() {}

    public static String resolveBenchmarkSupportStatus(String presetCode, Map<String, Object> metrics) {
        if (metrics != null) {
            String stored = str(metrics.get("benchmarkSupportStatus"));
            if (!stored.isBlank()) {
                return stored;
            }
        }
        RagExperimentalPresetCode parsed = parsePreset(presetCode);
        if (parsed == null) {
            return "";
        }
        if (ExperimentalPresetBenchmarkGate.blockReason(parsed).isPresent()) {
            return switch (parsed) {
                case P11, P12 -> "SINGLE_TURN_UNSUPPORTED";
                default -> "MULTI_TURN_EXTENSION_NOT_COMPARABLE";
            };
        }
        if (metrics != null && bool(metrics.get("requiresMultiTurn"))) {
            return "MULTI_TURN_EXTENSION_NOT_COMPARABLE";
        }
        if (metrics != null && bool(metrics.get("singleTurnBenchmarkSelectable"))) {
            return "SINGLE_TURN_SUPPORTED";
        }
        return "";
    }

    public static String resolvePresetOrder(String presetCode, Map<String, Object> metrics) {
        if (metrics != null && metrics.containsKey("protocolStageIndex")) {
            Object raw = metrics.get("protocolStageIndex");
            if (raw instanceof Number n) {
                return String.valueOf(n.intValue());
            }
            String text = str(raw);
            if (!text.isBlank()) {
                return text;
            }
        }
        String code = presetCode != null ? presetCode.trim() : "";
        Matcher matcher = PRESET_ORDER.matcher(code);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return "";
    }

    public static String resolveSingleTurnSupported(Map<String, Object> metrics) {
        if (metrics == null) {
            return "";
        }
        if (metrics.containsKey("singleTurnBenchmarkSelectable")) {
            return String.valueOf(metrics.get("singleTurnBenchmarkSelectable"));
        }
        return "";
    }

    public static String resolveComparableInSingleTurn(Map<String, Object> metrics) {
        if (metrics == null) {
            return "";
        }
        if (metrics.containsKey("comparableSingleTurnMetric")) {
            return String.valueOf(metrics.get("comparableSingleTurnMetric"));
        }
        return "";
    }

    public static String sanitizePresetLabel(String presetCode, String storedLabel, String catalogLabel) {
        String code = presetCode != null ? presetCode.trim() : "";
        String catalog = catalogLabel != null ? catalogLabel.trim() : "";
        if (!catalog.isBlank() && !catalog.equalsIgnoreCase(code)) {
            return catalog;
        }
        String stored = storedLabel != null ? storedLabel.trim() : "";
        if (!stored.isBlank() && !looksLikeModelId(stored) && !stored.equalsIgnoreCase(code)) {
            return stored;
        }
        return catalog.isBlank() ? stored : catalog;
    }

    private static boolean looksLikeModelId(String label) {
        return label.contains(":") || label.contains("/");
    }

    private static RagExperimentalPresetCode parsePreset(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return RagExperimentalPresetCode.tryParse(code.trim()).orElse(null);
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
