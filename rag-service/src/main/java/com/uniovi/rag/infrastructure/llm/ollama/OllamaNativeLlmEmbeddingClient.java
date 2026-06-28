package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

/**
 * Ollama-native {@link LlmEmbeddingClient} delegating to {@link OllamaEmbeddingModelFactory}
 * ({@code POST /api/embed} with legacy {@code /api/embeddings} fallback inside Spring AI).
 */
@Component
public class OllamaNativeLlmEmbeddingClient implements LlmEmbeddingClient {

    private final OllamaEmbeddingModelFactory embeddingModelFactory;

    public OllamaNativeLlmEmbeddingClient(OllamaEmbeddingModelFactory embeddingModelFactory) {
        this.embeddingModelFactory = embeddingModelFactory;
    }

    @Override
    public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
        EmbeddingModel embeddingModel = embeddingModelFactory.forModel(request.model());
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(request.texts(), null));
        List<float[]> vectors = new ArrayList<>();
        for (Embedding embedding : response.getResults()) {
            vectors.add(embedding.getOutput());
        }
        return new LlmEmbeddingResponse(request.model(), vectors, Map.of("provider", provider().name()));
    }

    @Override
    public LlmProvider provider() {
        return LlmProvider.OLLAMA_NATIVE;
    }
}
