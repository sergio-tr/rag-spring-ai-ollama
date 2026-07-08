package com.uniovi.rag.application.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeOverrideNormalizerTest {

    @Test
    void normalize_returnsEmptyWhenCandidateNullOrEmpty() {
        assertThat(RuntimeOverrideNormalizer.normalize(null, Map.of("topK", 5)))
                .isEqualTo(new RuntimeOverrideNormalizer.NormalizedOverride(Map.of(), java.util.List.of()));
        assertThat(RuntimeOverrideNormalizer.normalize(Map.of(), Map.of("topK", 5)))
                .isEqualTo(new RuntimeOverrideNormalizer.NormalizedOverride(Map.of(), java.util.List.of()));
    }

    @Test
    void normalize_keepsOnlyManualDifferencesAgainstBase() {
        Map<String, Object> base = Map.of("topK", 5, "similarityThreshold", 0.25);
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("topK", 12);
        candidate.put("similarityThreshold", 0.25);
        candidate.put("", "ignored");
        candidate.put("embeddingModel", "bge-m3");

        RuntimeOverrideNormalizer.NormalizedOverride normalized =
                RuntimeOverrideNormalizer.normalize(candidate, base);

        assertThat(normalized.runtimeOverride()).containsExactlyEntriesOf(Map.of("topK", 12, "embeddingModel", "bge-m3"));
        assertThat(normalized.manualOverrideKeys()).containsExactly("embeddingModel", "topK");
    }

    @Test
    void normalize_treatsNumericValuesAsEqualWhenDoubleEquivalent() {
        Map<String, Object> base = Map.of("similarityThreshold", 0.25);
        Map<String, Object> candidate = Map.of("similarityThreshold", 0.250);

        RuntimeOverrideNormalizer.NormalizedOverride normalized =
                RuntimeOverrideNormalizer.normalize(candidate, base);

        assertThat(normalized.runtimeOverride()).isEmpty();
        assertThat(normalized.manualOverrideKeys()).isEmpty();
    }
}
