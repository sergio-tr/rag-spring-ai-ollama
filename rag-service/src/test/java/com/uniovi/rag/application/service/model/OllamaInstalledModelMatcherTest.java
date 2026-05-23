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
        assertThat(OllamaInstalledModelMatcher.findMatchingInstalledNames("bge-m3", Set.of("bge-m3:latest")))
                .containsExactly("bge-m3:latest");
        assertThat(OllamaInstalledModelMatcher.pickBestInstalledName("bge-m3", List.of("bge-m3:latest")))
                .isEqualTo("bge-m3:latest");
    }
}
