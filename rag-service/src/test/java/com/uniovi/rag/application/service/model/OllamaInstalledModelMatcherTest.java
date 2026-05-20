package com.uniovi.rag.application.service.model;

import static org.assertj.core.api.Assertions.assertThat;

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
}
