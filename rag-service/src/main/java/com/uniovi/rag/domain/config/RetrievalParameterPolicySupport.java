package com.uniovi.rag.domain.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chat retrieval coherence: presets supply recommended {@code topK} / {@code similarityThreshold}; conversation
 * {@code runtimeOverride} wins when present. Policy metadata keys are never merged into runtime RAG configuration.
 */
public final class RetrievalParameterPolicySupport {

    private RetrievalParameterPolicySupport() {}

    public static boolean isPresetRetrievalLocked(Map<String, Object> presetValues) {
        if (presetValues == null || presetValues.isEmpty()) {
            return false;
        }
        Object policy = presetValues.get(RetrievalParameterKeys.RETRIEVAL_PARAMETER_POLICY);
        if (policy != null && RetrievalParameterPolicy.PRESET_LOCKED.name().equalsIgnoreCase(policy.toString().trim())) {
            return true;
        }
        Object lock = presetValues.get(RetrievalParameterKeys.LOCK_RETRIEVAL_PARAMETERS);
        return lock instanceof Boolean locked && locked;
    }

    /** Merges preset values into {@code target}; policy metadata keys are never merged. */
    public static void mergePresetLayer(Map<String, Object> target, Map<String, Object> presetValues) {
        if (presetValues == null || presetValues.isEmpty() || target == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : presetValues.entrySet()) {
            String key = entry.getKey();
            if (RetrievalParameterKeys.isPolicyMetadataKey(key)) {
                continue;
            }
            target.put(key, entry.getValue());
        }
    }

    /** Returns a copy of preset values with policy metadata removed (retrieval parameters kept). */
    public static Map<String, Object> stripPresetPolicyMetadata(Map<String, Object> presetValues) {
        if (presetValues == null || presetValues.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> stripped = new LinkedHashMap<>(presetValues);
        stripped.remove(RetrievalParameterKeys.LOCK_RETRIEVAL_PARAMETERS);
        stripped.remove(RetrievalParameterKeys.RETRIEVAL_PARAMETER_POLICY);
        return Map.copyOf(stripped);
    }

    /** @deprecated use {@link #stripPresetPolicyMetadata}; kept for call-site compatibility */
    @Deprecated
    public static Map<String, Object> stripUnlockedRetrievalParameters(Map<String, Object> presetValues) {
        return stripPresetPolicyMetadata(presetValues);
    }

    public static RetrievalParameterPolicy sourceForKey(
            String key,
            Map<String, Object> runtimeOverride,
            Map<String, Object> presetValues) {
        return sourceForKey(key, runtimeOverride, presetValues, null, null);
    }

    public static RetrievalParameterPolicy sourceForKey(
            String key,
            Map<String, Object> runtimeOverride,
            Map<String, Object> presetValues,
            Map<String, Object> projectValues,
            Map<String, Object> userValues) {
        String mode = RetrievalOverrideModeSupport.readMode(runtimeOverride);
        if (RetrievalOverrideModeSupport.CUSTOM.equals(mode)) {
            if (runtimeOverride != null && runtimeOverride.containsKey(key)) {
                return RetrievalParameterPolicy.CONVERSATION_CUSTOM;
            }
        }
        if (RetrievalOverrideModeSupport.ASSISTANT_DEFAULTS.equals(mode)) {
            return RetrievalParameterPolicy.USER_DEFAULTS;
        }
        if (RetrievalOverrideModeSupport.PROJECT_SETTINGS.equals(mode)) {
            if (projectValues != null && projectValues.containsKey(key)) {
                return RetrievalParameterPolicy.PROJECT_DEFAULTS;
            }
            if (userValues != null && userValues.containsKey(key)) {
                return RetrievalParameterPolicy.USER_DEFAULTS;
            }
            return RetrievalParameterPolicy.USER_DEFAULTS;
        }
        if (runtimeOverride != null && runtimeOverride.containsKey(key)) {
            return RetrievalParameterPolicy.CONVERSATION_CUSTOM;
        }
        if (presetValues != null && presetValues.containsKey(key)) {
            return RetrievalParameterPolicy.PRESET_LOCKED;
        }
        if (projectValues != null && projectValues.containsKey(key)) {
            return RetrievalParameterPolicy.PROJECT_DEFAULTS;
        }
        return RetrievalParameterPolicy.USER_DEFAULTS;
    }
}
