package com.uniovi.rag.infrastructure.llm.ollama;

import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.application.port.llm.LlmEmbeddingResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaNativeLlmEmbeddingClientTest {

    @Mock
    private OllamaEmbeddingModelFactory embeddingModelFactory;

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void delegatesToEmbeddingModelFactory() {
        when(embeddingModelFactory.forModel("mxbai-embed-large:latest")).thenReturn(embeddingModel);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {0.1f, 0.2f}, 0))));

        OllamaNativeLlmEmbeddingClient client = new OllamaNativeLlmEmbeddingClient(embeddingModelFactory);
        LlmEmbeddingResponse response = client.embed(LlmEmbeddingRequest.ofSingle("mxbai-embed-large:latest", "ping"));

        assertEquals("mxbai-embed-large:latest", response.model());
        assertEquals(1, response.embeddings().size());
        assertEquals(LlmProvider.OLLAMA_NATIVE, client.provider());
        assertEquals(2, response.embeddings().getFirst().length);
        verify(embeddingModelFactory).forModel("mxbai-embed-large:latest");
        verify(embeddingModel).call(any(EmbeddingRequest.class));
    }
}
