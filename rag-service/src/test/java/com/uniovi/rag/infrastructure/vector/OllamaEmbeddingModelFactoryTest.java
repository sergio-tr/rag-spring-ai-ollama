package com.uniovi.rag.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;

class OllamaEmbeddingModelFactoryTest {

    @Test
    void forModelRejectsBlankModelIds() {
        OllamaEmbeddingModelFactory factory =
                new OllamaEmbeddingModelFactory(new OllamaApi("http://localhost:11434"), ObservationRegistry.NOOP);

        assertThatThrownBy(() -> factory.forModel(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.forModel("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forModelBuildsOllamaEmbeddingModelForTrimmedTag() {
        OllamaEmbeddingModelFactory factory =
                new OllamaEmbeddingModelFactory(new OllamaApi("http://localhost:11434"), ObservationRegistry.NOOP);

        EmbeddingModel model = factory.forModel("  mxbai-embed-large  ");

        assertThat(model).isInstanceOf(OllamaEmbeddingModel.class);
    }
}
