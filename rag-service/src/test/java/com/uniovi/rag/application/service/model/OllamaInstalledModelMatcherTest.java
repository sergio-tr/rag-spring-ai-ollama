package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OllamaInstalledModelMatcherTest {

    @Test
    void matchesInstalledName_acceptsBaseNameWhenOllamaHasLatestTag() {
        assertThat(OllamaInstalledModelMatcher.matchesInstalledName("llama3", Set.of("llama3:latest")))
                .isTrue();
    }

    @Test
    void findMatchingInstalledNames_returnsTaggedVariants() {
        assertThat(OllamaInstalledModelMatcher.findMatchingInstalledNames("llama3", Set.of("llama3:latest", "other:1")))
                .containsExactly("llama3:latest");
    }

    @Test
    void findMatchingInstalledNames_normalizesBgeM3BaseName() {
        assertThat(OllamaInstalledModelMatcher.findMatchingInstalledNames(
                        "qwen3-embedding", Set.of("qwen3-embedding:latest")))
                .containsExactly("qwen3-embedding:latest");
        assertThat(OllamaInstalledModelMatcher.pickBestInstalledName(
                        "qwen3-embedding", List.of("qwen3-embedding:latest")))
                .isEqualTo("qwen3-embedding:latest");
    }
}
