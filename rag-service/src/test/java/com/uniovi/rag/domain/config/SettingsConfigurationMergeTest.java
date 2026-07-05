package com.uniovi.rag.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsConfigurationMergeTest {

    @Test
    void mergePatch_emptyPatchPreservesStored() {
        Map<String, Object> stored = Map.of("topK", 12, "similarityThreshold", 0.1);
        assertThat(SettingsConfigurationMerge.mergePatch(stored, Map.of())).isEqualTo(stored);
    }

    @Test
    void mergePatch_nullValueRemovesKey() {
        Map<String, Object> stored = new LinkedHashMap<>(Map.of("topK", 12, "similarityThreshold", 0.1));
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("topK", null);
        patch.put("similarityThreshold", 0.2);
        Map<String, Object> out = SettingsConfigurationMerge.mergePatch(stored, patch);
        assertThat(out).containsEntry("similarityThreshold", 0.2).doesNotContainKey("topK");
    }

    @Test
    void mergePatch_addsNewKeys() {
        Map<String, Object> out = SettingsConfigurationMerge.mergePatch(Map.of(), Map.of("topK", 8));
        assertThat(out).containsEntry("topK", 8);
    }
}
