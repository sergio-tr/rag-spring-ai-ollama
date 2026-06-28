package com.uniovi.rag.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LlmModelListNormalizerTest {

    @Test
    void preservesColonAndSlashInModelNames() {
        List<String> normalized =
                LlmModelListNormalizer.fromPropertyValues(
                        List.of(
                                " gemma3:4b , llama3.1:8b",
                                "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest"));
        assertThat(normalized)
                .containsExactly(
                        "gemma3:4b",
                        "llama3.1:8b",
                        "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
    }

    @Test
    void dropsBlankEntries() {
        assertThat(LlmModelListNormalizer.fromPropertyValues(List.of("a", "  ", "", "b"))).containsExactly("a", "b");
    }
}
