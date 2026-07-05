package com.uniovi.rag.domain.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.uniovi.rag.domain.runtime.RagConfig;
import java.util.Map;

/**
 * Conversation retrieval source mode ({@code retrievalOverrideMode}) reconciles which layer supplies
 * {@code topK} / {@code similarityThreshold} after the standard cascade merge.
 */
public final class RetrievalOverrideModeSupport {

    public static final String KEY = "retrievalOverrideMode";
    public static final String PRESET = "preset";
    public static final String ASSISTANT_DEFAULTS = "assistant_defaults";
    public static final String PROJECT_SETTINGS = "project_settings";
    public static final String CUSTOM = "custom";

    private RetrievalOverrideModeSupport() {}

    public static String readMode(Map<String, Object> runtimeOverride) {
        if (runtimeOverride == null || runtimeOverride.isEmpty()) {
            return null;
        }
        Object raw = runtimeOverride.get(KEY);
        if (raw == null) {
            return null;
        }
        String mode = raw.toString().trim();
        return switch (mode) {
            case PRESET, ASSISTANT_DEFAULTS, PROJECT_SETTINGS, CUSTOM -> mode;
            default -> null;
        };
    }

    public static String readMode(JsonNode conversationOverride, JsonNode requestOverride) {
        String fromRequest = readModeFromNode(requestOverride);
        if (fromRequest != null) {
            return fromRequest;
        }
        return readModeFromNode(conversationOverride);
    }

    public static void reconcileRetrievalOverridePatch(Map<String, Object> snapshot, Map<String, Object> patch) {
        if (patch == null || !patch.containsKey(KEY)) {
            return;
        }
        String mode = patch.get(KEY).toString().trim();
        if (PRESET.equals(mode)) {
            snapshot.remove(KEY);
            snapshot.remove(RetrievalParameterKeys.TOP_K);
            snapshot.remove(RetrievalParameterKeys.SIMILARITY_THRESHOLD);
            return;
        }
        if (ASSISTANT_DEFAULTS.equals(mode) || PROJECT_SETTINGS.equals(mode)) {
            snapshot.remove(RetrievalParameterKeys.TOP_K);
            snapshot.remove(RetrievalParameterKeys.SIMILARITY_THRESHOLD);
        }
    }

    public static void finalizePresetModeSnapshot(Map<String, Object> snapshot, Map<String, Object> patch) {
        if (patch == null || !patch.containsKey(KEY)) {
            return;
        }
        if (PRESET.equals(patch.get(KEY).toString().trim())) {
            snapshot.remove(KEY);
            snapshot.remove(RetrievalParameterKeys.TOP_K);
            snapshot.remove(RetrievalParameterKeys.SIMILARITY_THRESHOLD);
        }
    }

    public static RagConfig applyModeAwareRetrieval(
            RagConfig merged,
            JsonNode conversationOverride,
            JsonNode requestOverride,
            Map<String, Object> systemLayer,
            Map<String, Object> userLayer,
            Map<String, Object> projectLayer) {
        String mode = readMode(conversationOverride, requestOverride);
        if (mode == null || PRESET.equals(mode) || CUSTOM.equals(mode)) {
            return merged;
        }
        if (ASSISTANT_DEFAULTS.equals(mode)) {
            RetrievalPair pair = resolveAssistantPair(userLayer, systemLayer, merged);
            return withRetrievalValues(merged, pair.topK(), pair.similarityThreshold());
        }
        if (PROJECT_SETTINGS.equals(mode)) {
            RetrievalPair pair = resolveProjectPair(projectLayer, userLayer, systemLayer, merged);
            return withRetrievalValues(merged, pair.topK(), pair.similarityThreshold());
        }
        return merged;
    }

    private record RetrievalPair(int topK, double similarityThreshold) {}

    /** User layer pair, else system pair, else application defaults — never preset-merged fallback per key. */
    private static RetrievalPair resolveAssistantPair(
            Map<String, Object> userLayer, Map<String, Object> systemLayer, RagConfig applicationBase) {
        RetrievalPair fromUser = readPair(userLayer);
        if (fromUser != null) {
            return fromUser;
        }
        RetrievalPair fromSystem = readPair(systemLayer);
        if (fromSystem != null) {
            return fromSystem;
        }
        return new RetrievalPair(applicationBase.topK(), applicationBase.similarityThreshold());
    }

    /** Project topK/threshold resolved per key across project → user → system → application defaults. */
    private static RetrievalPair resolveProjectPair(
            Map<String, Object> projectLayer,
            Map<String, Object> userLayer,
            Map<String, Object> systemLayer,
            RagConfig applicationBase) {
        int topK =
                firstInt(
                        projectLayer,
                        userLayer,
                        systemLayer,
                        applicationBase.topK());
        double similarityThreshold =
                firstDouble(
                        projectLayer,
                        userLayer,
                        systemLayer,
                        applicationBase.similarityThreshold());
        return new RetrievalPair(topK, similarityThreshold);
    }

    private static int firstInt(
            Map<String, Object> primary,
            Map<String, Object> secondary,
            Map<String, Object> tertiary,
            int fallback) {
        Integer value = readInt(primary, RetrievalParameterKeys.TOP_K);
        if (value != null) {
            return value;
        }
        value = readInt(secondary, RetrievalParameterKeys.TOP_K);
        if (value != null) {
            return value;
        }
        value = readInt(tertiary, RetrievalParameterKeys.TOP_K);
        return value != null ? value : fallback;
    }

    private static double firstDouble(
            Map<String, Object> primary,
            Map<String, Object> secondary,
            Map<String, Object> tertiary,
            double fallback) {
        Double value = readDouble(primary, RetrievalParameterKeys.SIMILARITY_THRESHOLD);
        if (value != null) {
            return value;
        }
        value = readDouble(secondary, RetrievalParameterKeys.SIMILARITY_THRESHOLD);
        if (value != null) {
            return value;
        }
        value = readDouble(tertiary, RetrievalParameterKeys.SIMILARITY_THRESHOLD);
        return value != null ? value : fallback;
    }

    private static RetrievalPair readPair(Map<String, Object> layer) {
        if (layer == null || layer.isEmpty()) {
            return null;
        }
        Integer topK = readInt(layer, RetrievalParameterKeys.TOP_K);
        Double threshold = readDouble(layer, RetrievalParameterKeys.SIMILARITY_THRESHOLD);
        if (topK == null || threshold == null) {
            return null;
        }
        return new RetrievalPair(topK, threshold);
    }

    private static String readModeFromNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return null;
        }
        JsonNode raw = node.get(KEY);
        if (raw == null || raw.isNull() || !raw.isTextual()) {
            return null;
        }
        return switch (raw.asText().trim()) {
            case PRESET, ASSISTANT_DEFAULTS, PROJECT_SETTINGS, CUSTOM -> raw.asText().trim();
            default -> null;
        };
    }

    private static Integer readInt(Map<String, Object> layer, String key) {
        if (layer == null || layer.isEmpty()) {
            return null;
        }
        Object raw = layer.get(key);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static Double readDouble(Map<String, Object> layer, String key) {
        if (layer == null || layer.isEmpty()) {
            return null;
        }
        Object raw = layer.get(key);
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private static RagConfig withRetrievalValues(RagConfig base, int topK, double similarityThreshold) {
        return new RagConfig(
                base.expansionEnabled(),
                base.nerEnabled(),
                base.toolsEnabled(),
                base.metadataEnabled(),
                base.reasoningEnabled(),
                base.rankerEnabled(),
                base.postRetrievalEnabled(),
                base.functionCallingEnabled(),
                base.useRetrieval(),
                base.useAdvisor(),
                base.clarificationEnabled(),
                base.memoryEnabled(),
                base.adaptiveRoutingEnabled(),
                base.judgeEnabled(),
                base.deterministicToolRoutingEnabled(),
                topK,
                similarityThreshold,
                base.llmModel(),
                base.embeddingModel(),
                base.classifierModelId(),
                base.reasoningStrategy(),
                base.naiveFullCorpusInPromptEnabled(),
                base.naiveFullCorpusMaxChars(),
                base.advancedRetrievalMaxContextChars(),
                base.corpusGroundedDirectWorkflow(),
                base.functionCallingBackendProposalEnabled(),
                base.functionCallingNativeProviderEnabled(),
                base.materializationStrategy());
    }
}
