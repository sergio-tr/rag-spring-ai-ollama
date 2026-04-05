package com.uniovi.rag.configuration;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic Spring AI beans for profile {@code e2e}: Playwright fullstack and CI without Ollama.
 * Replaces provider {@link EmbeddingModel} and {@link ChatModel} via {@link Primary}.
 */
@Configuration
@Profile("e2e")
public class RagE2eAiStubConfiguration {

    static final int E2E_EMBEDDING_DIMENSIONS = 1024;

    @Bean
    @Primary
    public EmbeddingModel e2eEmbeddingModel() {
        return new E2eStubEmbeddingModel();
    }

    @Bean
    @Primary
    public ChatModel e2eChatModel() {
        return new E2eStubChatModel();
    }

    static final class E2eStubEmbeddingModel implements EmbeddingModel {

        private final float[] vector;

        E2eStubEmbeddingModel() {
            vector = new float[E2E_EMBEDDING_DIMENSIONS];
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

        @Override
        public int dimensions() {
            return E2E_EMBEDDING_DIMENSIONS;
        }
    }

    static final class E2eStubChatModel implements ChatModel {

        static final String E2E_REPLY = "E2E stub reply: RAG pipeline ran without a live Ollama server.";

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(E2E_REPLY))));
        }
    }
}
