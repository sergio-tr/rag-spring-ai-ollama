package com.uniovi.rag.application.service.llm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class LlmCatalogApiServiceEmbeddingDimensionsTest {

    @Test
    void resolvesMxbaiHfPathAs1024() {
        OptionalInt dims =
                LlmCatalogApiService.resolveEmbeddingDimensions(
                        "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
        assertThat(dims).hasValue(1024);
    }

    @Test
    void resolvesNomicAs768() {
        assertThat(LlmCatalogApiService.resolveEmbeddingDimensions("nomic-embed-text:latest")).hasValue(768);
    }
}
