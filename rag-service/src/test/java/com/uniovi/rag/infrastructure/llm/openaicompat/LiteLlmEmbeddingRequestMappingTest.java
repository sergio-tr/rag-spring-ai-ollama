package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.service.embedding.EmbeddingBenchmarkRuntimeParameters;
import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.embedding.EmbeddingRequestOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiteLlmEmbeddingRequestMappingTest {

    @Test
    void mapsEncodingFormatAndDimensionsWhenConfigured() {
        Map<String, Object> additional =
                EmbeddingBenchmarkRuntimeParameters.mergeIntoRuntimeParameters(
                        Map.of(),
                        new EmbeddingRequestOptions("base64", 256, null, 20),
                        null,
                        null);
        LlmEmbeddingRequest request =
                new LlmEmbeddingRequest("bge-m3", List.of("hello"), null, additional);

        OpenAiEmbeddingRequest apiRequest = OpenAiCompatibleEmbeddingMapper.toApiRequest(request);

        assertThat(apiRequest.encodingFormat()).isEqualTo("base64");
        assertThat(apiRequest.dimensions()).isEqualTo(256);
        assertThat(apiRequest.model()).isEqualTo("bge-m3");
    }

    @Test
    void omitsDimensionsWhenUnsupportedModelLeavesFieldUnset() {
        Map<String, Object> additional = Map.of();
        LlmEmbeddingRequest request =
                new LlmEmbeddingRequest("mxbai-embed-large", List.of("hello"), null, additional);

        OpenAiEmbeddingRequest apiRequest = OpenAiCompatibleEmbeddingMapper.toApiRequest(request);

        assertThat(apiRequest.dimensions()).isNull();
        assertThat(apiRequest.encodingFormat()).isNull();
    }

    @Test
    void readsNestedEmbeddingOptionsFromRuntimePayload() {
        Map<String, Object> runtime =
                Map.of(
                        EmbeddingConfigurationKeys.RUNTIME_EMBEDDING_OPTIONS,
                        Map.of("encodingFormat", "float", "timeoutSeconds", 15));
        Map<String, Object> additional = EmbeddingBenchmarkRuntimeParameters.toLlmAdditionalParameters(
                EmbeddingBenchmarkRuntimeParameters.readEmbeddingOptions(runtime));

        assertThat(additional.get("encoding_format")).isEqualTo("float");
    }
}
