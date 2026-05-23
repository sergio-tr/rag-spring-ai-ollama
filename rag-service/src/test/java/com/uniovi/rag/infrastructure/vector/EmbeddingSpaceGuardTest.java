package com.uniovi.rag.infrastructure.vector;

import com.uniovi.rag.configuration.RagVectorProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingSpaceGuardTest {

    @Mock
    private OllamaEmbeddingModelFactory embeddingModelFactory;

    @Test
    void assertFitsPhysicalVectorColumnReturning_throwsWhenModelOutputWidthDiffersFromStore() {
        when(embeddingModelFactory.forModel("nomic-embed-text")).thenReturn(new FixedWidthEmbeddingModel(768));
        RagVectorProperties props = new RagVectorProperties(1024, true);
        EmbeddingSpaceGuard guard = new EmbeddingSpaceGuard(embeddingModelFactory, props);

        assertThatThrownBy(() -> guard.assertFitsPhysicalVectorColumnReturning("nomic-embed-text"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                            assertThat(r.getReason()).contains("EMBEDDING_DIMENSION_MISMATCH");
                        });
    }

    @Test
    void assertFitsPhysicalVectorColumnReturning_returnsWidthWhenModelMatchesStore() {
        when(embeddingModelFactory.forModel("mxbai-embed-large")).thenReturn(new FixedWidthEmbeddingModel(1024));
        RagVectorProperties props = new RagVectorProperties(1024, true);
        EmbeddingSpaceGuard guard = new EmbeddingSpaceGuard(embeddingModelFactory, props);

        assertThat(guard.assertFitsPhysicalVectorColumnReturning("mxbai-embed-large")).isEqualTo(1024);
    }

    @Test
    void assertFitsPhysicalVectorColumnReturning_acceptsBgeM3WhenStoreIs1024Wide() {
        when(embeddingModelFactory.forModel("qwen3-embedding:latest")).thenReturn(new FixedWidthEmbeddingModel(1024));
        RagVectorProperties props = new RagVectorProperties(1024, true);
        EmbeddingSpaceGuard guard = new EmbeddingSpaceGuard(embeddingModelFactory, props);

        assertThat(guard.assertFitsPhysicalVectorColumnReturning("qwen3-embedding:latest")).isEqualTo(1024);
    }

    /** Minimal {@link EmbeddingModel} that reports a fixed vector width for every embed/call path. */
    private static final class FixedWidthEmbeddingModel implements EmbeddingModel {

        private final float[] vector;

        FixedWidthEmbeddingModel(int dimensions) {
            vector = new float[dimensions];
            Arrays.fill(vector, 0.01f);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(Arrays.copyOf(vector, vector.length), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return Arrays.copyOf(vector, vector.length);
        }

        @Override
        public float[] embed(String text) {
            return Arrays.copyOf(vector, vector.length);
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            List<float[]> out = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                out.add(Arrays.copyOf(vector, vector.length));
            }
            return out;
        }
    }
}
