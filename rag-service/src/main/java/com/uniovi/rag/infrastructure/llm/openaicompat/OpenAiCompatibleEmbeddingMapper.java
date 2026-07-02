package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.application.service.embedding.EmbeddingBenchmarkRuntimeParameters;
import com.uniovi.rag.domain.embedding.EmbeddingRequestOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class OpenAiCompatibleEmbeddingMapper {

    private OpenAiCompatibleEmbeddingMapper() {}

    static OpenAiEmbeddingRequest toApiRequest(LlmEmbeddingRequest request) {
        Object input = request.texts().size() == 1 ? request.texts().getFirst() : request.texts();
        EmbeddingRequestOptions options = readOptions(request);
        return new OpenAiEmbeddingRequest(
                request.model(),
                input,
                options.encodingFormat(),
                options.dimensions(),
                options.user());
    }

    private static EmbeddingRequestOptions readOptions(LlmEmbeddingRequest request) {
        Map<String, Object> additional =
                request.additionalParameters() != null ? request.additionalParameters() : Map.of();
        return EmbeddingBenchmarkRuntimeParameters.readEmbeddingOptions(additional);
    }

    static LlmEmbeddingResponse toPortResponse(OpenAiEmbeddingResponse apiResponse, String requestedModel) {
        if (apiResponse.data() == null || apiResponse.data().isEmpty()) {
            throw OpenAiCompatibleLlmException.invalidResponse("embeddings response has no data");
        }
        List<OpenAiEmbeddingDataDto> sorted = new ArrayList<>(apiResponse.data());
        sorted.sort(Comparator.comparingInt(d -> d.index() != null ? d.index() : 0));
        List<float[]> vectors = new ArrayList<>();
        for (OpenAiEmbeddingDataDto item : sorted) {
            if (item.embedding() == null || item.embedding().isEmpty()) {
                throw OpenAiCompatibleLlmException.invalidResponse("embedding vector is empty");
            }
            vectors.add(toFloatArray(item.embedding()));
        }
        String model = apiResponse.model() != null && !apiResponse.model().isBlank() ? apiResponse.model() : requestedModel;
        return new LlmEmbeddingResponse(model, vectors, Map.of("provider", "OPENAI_COMPATIBLE"));
    }

    private static float[] toFloatArray(List<Double> values) {
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i).floatValue();
        }
        return out;
    }
}
