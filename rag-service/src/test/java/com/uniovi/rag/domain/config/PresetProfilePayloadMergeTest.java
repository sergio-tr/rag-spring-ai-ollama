package com.uniovi.rag.domain.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PresetProfilePayloadMergeTest {

    @Test
    void emptyProfiles_preservesPresetKeys() {
        Map<String, Object> preset = new LinkedHashMap<>(Map.of("topK", 3));
        Map<String, Object> merged = PresetProfilePayloadMerge.merge(preset, List.of());
        assertThat(merged).containsEntry("topK", 3);
    }

    @Test
    void orderedProfiles_laterOverwritesEarlier() {
        Map<String, Object> preset = new LinkedHashMap<>(Map.of("k", "preset"));
        Map<String, Object> p1 = Map.of("k", "first", "a", 1);
        Map<String, Object> p2 = Map.of("k", "second", "b", 2);
        Map<String, Object> merged = PresetProfilePayloadMerge.merge(preset, List.of(p1, p2));
        assertThat(merged)
                .containsEntry("k", "second")
                .containsEntry("a", 1)
                .containsEntry("b", 2);
    }

    @Test
    void nullPresetValues_treatedAsEmpty() {
        Map<String, Object> merged = PresetProfilePayloadMerge.merge(null, List.of(Map.of("x", 1)));
        assertThat(merged).containsEntry("x", 1);
    }
}
