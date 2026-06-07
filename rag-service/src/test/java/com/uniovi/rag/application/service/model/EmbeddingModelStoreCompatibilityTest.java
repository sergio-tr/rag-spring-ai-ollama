package com.uniovi.rag.application.service.model;

import com.uniovi.rag.configuration.RagVectorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingModelStoreCompatibilityTest {

    private final EmbeddingModelStoreCompatibility compatibility =
            new EmbeddingModelStoreCompatibility(new RagVectorProperties(1024, true));

    @Test
    void excludesNomicAndQwen3Tags() {
        assertThat(compatibility.isSelectableForLabEmbeddingBenchmark("nomic-embed-text:latest")).isFalse();
        assertThat(compatibility.isSelectableForLabEmbeddingBenchmark("qwen3-embedding:latest")).isFalse();
    }

    @Test
    void includesStoreCompatibleCuratedTags() {
        assertThat(compatibility.isSelectableForLabEmbeddingBenchmark("mxbai-embed-large:latest")).isTrue();
        assertThat(compatibility.isSelectableForLabEmbeddingBenchmark("mxbai-embed-large")).isTrue();
    }

    @Test
    void unknownAllowlistTagPassesWhenNotPatternExcluded() {
        assertThat(compatibility.isSelectableForLabEmbeddingBenchmark("bge-m3:latest")).isTrue();
    }

    @Test
    void rejectsBlankNames() {
        assertThat(compatibility.isSelectableForLabEmbeddingBenchmark("")).isFalse();
        assertThat(compatibility.isSelectableForLabEmbeddingBenchmark("   ")).isFalse();
    }
}
