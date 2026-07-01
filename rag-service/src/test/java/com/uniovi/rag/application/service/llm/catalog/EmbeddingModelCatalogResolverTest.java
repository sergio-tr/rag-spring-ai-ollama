package com.uniovi.rag.application.service.llm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmOllamaDefaults;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbeddingModelCatalogResolverTest {

    private EmbeddingModelCatalogResolver resolver;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        LlmOllamaDefaults ollama = new LlmOllamaDefaults();
        ollama.setDefaultEmbeddingModel("mxbai-embed-large:latest");
        ollama.setAvailableEmbeddingModels(
                List.of("hf.co/mixedbread-ai/mxbai-embed-large-v1:latest", "mxbai-embed-large", "mxbai-embed-large:latest"));
        properties.setOllama(ollama);
        properties.setOpenAiCompatible(new LlmOpenAiCompatibleDefaults());
        LlmModelCatalogService catalog = new LlmModelCatalogService(properties);
        resolver = new EmbeddingModelCatalogResolver(catalog, properties);
    }

    @Test
    void resolve_mapsLegacyShortIdToCatalogEntry() {
        String resolved = resolver.resolve(LlmProvider.OLLAMA_NATIVE, "mxbai-embed-large");
        assertThat(resolved).isIn("mxbai-embed-large", "mxbai-embed-large:latest", "hf.co/mixedbread-ai/mxbai-embed-large-v1:latest");
    }

    @Test
    void resolve_honorsExactCatalogId() {
        assertThat(resolver.resolve(LlmProvider.OLLAMA_NATIVE, "mxbai-embed-large:latest"))
                .isEqualTo("mxbai-embed-large:latest");
    }
}
