package com.uniovi.rag.application.service.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.uniovi.rag.domain.config.RetrievalOverrideModeSupport;
import com.uniovi.rag.domain.config.RetrievalParameterKeys;

/**
 * Conversation-level configuration snapshot semantics for Chat.
 *
 * <p>The persisted {@code runtime_override_jsonb} column stores a full custom conversation
 * configuration snapshot (not a partial diff). PATCH bodies merge into that snapshot.
 */
public final class ConversationConfigurationSupport {

    public static final String MODE_PRESET = "PRESET";
    public static final String MODE_CUSTOM = "CUSTOM";

    private ConversationConfigurationSupport() {}

    public static Map<String, Object> sanitizeSnapshot(Map<String, Object> raw) {
        return ChatRuntimeCompatibilitySupport.copyWithoutNonRuntimeOverrideKeys(raw);
    }

    public static Map<String, Object> seedRuntimeConfigurableFromBase(Map<String, Object> baseEffectiveConfig) {
        if (baseEffectiveConfig == null || baseEffectiveConfig.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return sanitizeSnapshot(new LinkedHashMap<>(baseEffectiveConfig));
    }

    /**
     * Merges {@code configPatch} into the persisted snapshot. When the snapshot is empty and the patch is
     * non-empty, seeds from {@code baseEffectiveConfig} first so explicit false values are never lost.
     */
    public static Map<String, Object> mergeConfigPatch(
            Map<String, Object> persistedSnapshot,
            Map<String, Object> configPatch,
            Map<String, Object> baseEffectiveConfig) {
        Map<String, Object> snapshot =
                persistedSnapshot != null && !persistedSnapshot.isEmpty()
                        ? new LinkedHashMap<>(sanitizeSnapshot(persistedSnapshot))
                        : new LinkedHashMap<>();
        Map<String, Object> patch =
                configPatch != null && !configPatch.isEmpty()
                        ? sanitizeSnapshot(configPatch)
                        : Map.of();
        if (patch.isEmpty()) {
            return snapshot.isEmpty() ? Map.of() : Map.copyOf(snapshot);
        }
        if (snapshot.isEmpty()) {
            snapshot.putAll(seedRuntimeConfigurableFromBase(baseEffectiveConfig));
        }
        RetrievalOverrideModeSupport.reconcileRetrievalOverridePatch(snapshot, patch);
        snapshot.putAll(patch);
        ensureCustomRetrievalPair(snapshot, patch, baseEffectiveConfig);
        RetrievalOverrideModeSupport.finalizePresetModeSnapshot(snapshot, patch);
        return snapshot.isEmpty() ? Map.of() : Map.copyOf(snapshot);
    }

    /** Custom mode must always persist both retrieval numeric keys together. */
    private static void ensureCustomRetrievalPair(
            Map<String, Object> snapshot, Map<String, Object> patch, Map<String, Object> baseEffectiveConfig) {
        if (patch == null || !patch.containsKey(RetrievalOverrideModeSupport.KEY)) {
            return;
        }
        if (!RetrievalOverrideModeSupport.CUSTOM.equals(patch.get(RetrievalOverrideModeSupport.KEY).toString().trim())) {
            return;
        }
        boolean hasTopK = snapshot.containsKey(RetrievalParameterKeys.TOP_K);
        boolean hasThreshold = snapshot.containsKey(RetrievalParameterKeys.SIMILARITY_THRESHOLD);
        if (hasTopK && hasThreshold) {
            return;
        }
        if (baseEffectiveConfig != null) {
            if (!hasTopK && baseEffectiveConfig.get(RetrievalParameterKeys.TOP_K) instanceof Number topK) {
                snapshot.put(RetrievalParameterKeys.TOP_K, topK.intValue());
            }
            if (!hasThreshold
                    && baseEffectiveConfig.get(RetrievalParameterKeys.SIMILARITY_THRESHOLD) instanceof Number threshold) {
                snapshot.put(RetrievalParameterKeys.SIMILARITY_THRESHOLD, threshold.doubleValue());
            }
        }
    }

    public static String configurationMode(Map<String, Object> snapshot) {
        return snapshot == null || snapshot.isEmpty() ? MODE_PRESET : MODE_CUSTOM;
    }

    public static List<String> manualOverrideKeys(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(snapshot.keySet());
        keys.sort(Comparator.naturalOrder());
        return List.copyOf(keys);
    }
}
