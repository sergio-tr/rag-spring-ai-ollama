package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationConfigurationSupportTest {

    private static Map<String, Object> basePreset() {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("useAdvisor", true);
        base.put("rankerEnabled", true);
        base.put("postRetrievalEnabled", true);
        base.put("topK", 8);
        base.put("similarityThreshold", 0.25);
        base.put("embeddingModel", "nomic-embed-text");
        return base;
    }

    @Test
    void mergeConfigPatch_seedsFromBaseOnFirstEdit() {
        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        Map.of(), Map.of("useAdvisor", false), basePreset());

        assertThat(merged).containsEntry("useAdvisor", false);
        assertThat(merged).containsEntry("rankerEnabled", true);
        assertThat(merged).containsEntry("postRetrievalEnabled", true);
        assertThat(merged).doesNotContainKey("embeddingModel");
    }

    @Test
    void mergeConfigPatch_accumulatesExplicitFalseValues() {
        Map<String, Object> afterAdvisor =
                ConversationConfigurationSupport.mergeConfigPatch(
                        Map.of(), Map.of("useAdvisor", false), basePreset());
        Map<String, Object> afterRanker =
                ConversationConfigurationSupport.mergeConfigPatch(
                        afterAdvisor, Map.of("rankerEnabled", false), basePreset());

        assertThat(afterRanker).containsEntry("useAdvisor", false);
        assertThat(afterRanker).containsEntry("rankerEnabled", false);
        assertThat(afterRanker).containsEntry("postRetrievalEnabled", true);
    }

    @Test
    void mergeConfigPatch_missingKeysDoNotDeleteExistingCustomValues() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("useAdvisor", false);
        snapshot.put("rankerEnabled", false);

        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        snapshot, Map.of("postRetrievalEnabled", false), basePreset());

        assertThat(merged).containsEntry("useAdvisor", false);
        assertThat(merged).containsEntry("rankerEnabled", false);
        assertThat(merged).containsEntry("postRetrievalEnabled", false);
    }

    @Test
    void mergeConfigPatch_rejectsIndexBoundKeys() {
        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        Map.of(),
                        Map.of("useAdvisor", false, "embeddingModel", "other"),
                        basePreset());

        assertThat(merged).containsEntry("useAdvisor", false);
        assertThat(merged).doesNotContainKey("embeddingModel");
    }

    @Test
    void mergeConfigPatch_presetModeRemovesRetrievalOverrides() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("retrievalOverrideMode", "custom");
        snapshot.put("topK", 12);
        snapshot.put("similarityThreshold", 0.4);
        snapshot.put("useAdvisor", false);

        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        snapshot, Map.of("retrievalOverrideMode", "preset"), basePreset());

        assertThat(merged).containsEntry("useAdvisor", false);
        assertThat(merged).doesNotContainKey("retrievalOverrideMode");
        assertThat(merged).doesNotContainKey("topK");
        assertThat(merged).doesNotContainKey("similarityThreshold");
    }

    @Test
    void mergeConfigPatch_assistantModeRemovesNumericKeysOnly() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("retrievalOverrideMode", "custom");
        snapshot.put("topK", 12);
        snapshot.put("similarityThreshold", 0.4);

        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        snapshot,
                        Map.of("retrievalOverrideMode", "assistant_defaults"),
                        basePreset());

        assertThat(merged).containsEntry("retrievalOverrideMode", "assistant_defaults");
        assertThat(merged).doesNotContainKey("topK");
        assertThat(merged).doesNotContainKey("similarityThreshold");
    }

    @Test
    void mergeConfigPatch_projectModeRemovesNumericKeysOnly() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("retrievalOverrideMode", "custom");
        snapshot.put("topK", 12);
        snapshot.put("similarityThreshold", 0.4);

        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        snapshot,
                        Map.of("retrievalOverrideMode", "project_settings"),
                        basePreset());

        assertThat(merged).containsEntry("retrievalOverrideMode", "project_settings");
        assertThat(merged).doesNotContainKey("topK");
        assertThat(merged).doesNotContainKey("similarityThreshold");
    }

    @Test
    void mergeConfigPatch_customModeKeepsNumericValues() {
        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        Map.of(),
                        Map.of(
                                "retrievalOverrideMode",
                                "custom",
                                "topK",
                                12,
                                "similarityThreshold",
                                0.4),
                        basePreset());

        assertThat(merged).containsEntry("retrievalOverrideMode", "custom");
        assertThat(merged).containsEntry("topK", 12);
        assertThat(merged).containsEntry("similarityThreshold", 0.4);
    }

    @Test
    void mergeConfigPatch_customModeFillsMissingThresholdFromBase() {
        Map<String, Object> merged =
                ConversationConfigurationSupport.mergeConfigPatch(
                        Map.of(),
                        Map.of("retrievalOverrideMode", "custom", "topK", 12),
                        basePreset());

        assertThat(merged).containsEntry("retrievalOverrideMode", "custom");
        assertThat(merged).containsEntry("topK", 12);
        assertThat(merged).containsEntry("similarityThreshold", 0.25);
    }

    @Test
    void configurationMode_reflectsSnapshotPresence() {
        assertThat(ConversationConfigurationSupport.configurationMode(Map.of()))
                .isEqualTo(ConversationConfigurationSupport.MODE_PRESET);
        assertThat(ConversationConfigurationSupport.configurationMode(Map.of("useAdvisor", false)))
                .isEqualTo(ConversationConfigurationSupport.MODE_CUSTOM);
    }
}
