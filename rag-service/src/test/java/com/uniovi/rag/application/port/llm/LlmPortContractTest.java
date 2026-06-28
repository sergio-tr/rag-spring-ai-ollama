package com.uniovi.rag.application.port.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmPortContractTest {

    @Test
    void chatRequest_factoryBuildsSystemAndUserMessages() {
        LlmChatRequest request = LlmChatRequest.of("gemma3:4b", "You are helpful.", "Hola", 0.1, 30_000, Map.of("topK", 40));

        assertEquals("gemma3:4b", request.model());
        assertEquals(2, request.messages().size());
        assertEquals(LlmChatRole.SYSTEM, request.messages().get(0).role());
        assertEquals("You are helpful.", request.messages().get(0).content());
        assertEquals(LlmChatRole.USER, request.messages().get(1).role());
        assertEquals(0.1, request.temperature());
        assertEquals(30_000, request.timeoutMs());
        assertEquals(40, request.additionalParameters().get("topK"));
    }

    @Test
    void chatRequest_rejectsBlankModel() {
        assertThrows(IllegalArgumentException.class, () -> LlmChatRequest.of(" ", "sys", "user"));
    }

    @Test
    void chatRequest_rejectsEmptyMessages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LlmChatRequest("gemma3:4b", List.of(), null, null, Map.of()));
    }

    @Test
    void embeddingRequest_ofSingleWrapsText() {
        LlmEmbeddingRequest request = LlmEmbeddingRequest.ofSingle("mxbai-embed-large:latest", "ping");
        assertEquals("mxbai-embed-large:latest", request.model());
        assertEquals(List.of("ping"), request.texts());
    }

    @Test
    void chatResponse_defaultsEmptyContent() {
        LlmChatResponse response = new LlmChatResponse(null, "gemma3:4b", "stop", null, null);
        assertEquals("", response.content());
        assertEquals("gemma3:4b", response.model());
    }

    @Test
    void fakeClient_implementsContract() {
        LlmChatClient client =
                new LlmChatClient() {
                    @Override
                    public LlmChatResponse chat(LlmChatRequest request) {
                        return new LlmChatResponse(
                                "echo:" + request.messages().getLast().content(),
                                request.model(),
                                "stop",
                                new LlmTokenUsage(1, 1, 2),
                                Map.of());
                    }

                    @Override
                    public LlmProvider provider() {
                        return LlmProvider.OLLAMA_NATIVE;
                    }
                };

        LlmChatResponse response = client.chat(LlmChatRequest.of("m", null, "hi"));
        assertEquals("echo:hi", response.content());
        assertEquals(LlmProvider.OLLAMA_NATIVE, client.provider());
    }

    @Test
    void fakeEmbeddingClient_implementsContract() {
        LlmEmbeddingClient client =
                new LlmEmbeddingClient() {
                    @Override
                    public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
                        return new LlmEmbeddingResponse(
                                request.model(), List.of(new float[] {0.1f, 0.2f}), Map.of());
                    }

                    @Override
                    public LlmProvider provider() {
                        return LlmProvider.OPENAI_COMPATIBLE;
                    }
                };

        LlmEmbeddingResponse response = client.embed(LlmEmbeddingRequest.ofSingle("emb", "a"));
        assertEquals(1, response.embeddings().size());
        assertEquals(2, response.embeddings().getFirst().length);
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, client.provider());
    }
}
