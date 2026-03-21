package com.uniovi.rag.testsupport;

import org.mockito.Mockito;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * In-memory stubs for Spring AI beans so {@code @SpringBootTest} does not call a real Ollama server.
 * Activated only with profile {@code test} (see {@code @Import} + {@code @ActiveProfiles("test")} on tests).
 */
@TestConfiguration
@Profile("test")
public class TestAiStubConfiguration {

    /** Matches pgvector / app config (mxbai-embed-large dimension). */
    public static final int EMBEDDING_DIM = 1024;

    @Bean
    @Primary
    public EmbeddingModel testEmbeddingModel() {
        EmbeddingModel model = Mockito.mock(EmbeddingModel.class);
        float[] vec = new float[EMBEDDING_DIM];
        Arrays.fill(vec, 0.01f);

        Mockito.lenient().when(model.embed(anyString())).thenReturn(vec);
        Mockito.lenient().when(model.embed(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> texts = invocation.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                out.add(Arrays.copyOf(vec, vec.length));
            }
            return out;
        });
        Mockito.lenient().when(model.dimensions()).thenReturn(EMBEDDING_DIM);
        Mockito.lenient().when(model.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest req = invocation.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = req.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(Arrays.copyOf(vec, vec.length), i));
            }
            return new EmbeddingResponse(embeddings);
        });
        return model;
    }

    @Bean
    @Primary
    public ChatModel testChatModel() {
        ChatModel model = Mockito.mock(ChatModel.class);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(
                        "No relevant information found. (test stub)"))));
        Mockito.lenient().when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }
}
