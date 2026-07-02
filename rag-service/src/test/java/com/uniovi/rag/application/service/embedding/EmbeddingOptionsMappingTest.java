package com.uniovi.rag.application.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.embedding.EmbeddingRequestOptions;
import com.uniovi.rag.domain.embedding.IndexingRequestOptions;
import com.uniovi.rag.domain.embedding.RetrievalRequestOptions;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmbeddingOptionsMappingTest {

    @Test
    void readsNestedAndFlatRetrievalOptionsWithBackwardCompat() {
        Map<String, Object> runtime =
                Map.of(
                        "topK",
                        7,
                        EmbeddingConfigurationKeys.RUNTIME_RETRIEVAL_OPTIONS,
                        Map.of("similarityThreshold", 0.42, "materializationStrategy", "HYBRID"));

        RetrievalRequestOptions options = EmbeddingBenchmarkRuntimeParameters.readRetrievalOptions(runtime);

        assertThat(options.topK()).isEqualTo(7);
        assertThat(options.similarityThreshold()).isEqualTo(0.42);
        assertThat(options.materializationStrategy()).isEqualTo("HYBRID");
    }

    @Test
    void mergeIntoRuntimeParametersExportsNestedGroupsAndFlatTopK() {
        Map<String, Object> merged =
                EmbeddingBenchmarkRuntimeParameters.mergeIntoRuntimeParameters(
                        Map.of(),
                        new EmbeddingRequestOptions("float", 512, "trace-user", 30),
                        new RetrievalRequestOptions(8, 0.55, "CHUNK_LEVEL"),
                        new IndexingRequestOptions(16, 2048, true, "END"));

        assertThat(merged.get("topK")).isEqualTo(8);
        assertThat(merged.get("similarityThreshold")).isEqualTo(0.55);
        assertThat(merged.get(EmbeddingConfigurationKeys.RUNTIME_EMBEDDING_OPTIONS))
                .isEqualTo(
                        Map.of(
                                "encodingFormat",
                                "float",
                                "dimensions",
                                512,
                                "user",
                                "trace-user",
                                "timeoutSeconds",
                                30));
    }
}
