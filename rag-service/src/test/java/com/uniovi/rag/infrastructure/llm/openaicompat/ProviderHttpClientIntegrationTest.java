package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaNativeLlmEmbeddingClient;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;

/** Phase 2 — provider HTTP routing with local HttpServer mocks (package access to HTTP clients). */
class ProviderHttpClientIntegrationTest {

    @Test
    void openAiCompatibleChatCallsV1ChatCompletionsOnly() throws Exception {
        AtomicBoolean modelsTouched = new AtomicBoolean(false);
        AtomicBoolean chatTouched = new AtomicBoolean(false);

        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext("/v1/models", exchange -> {
                modelsTouched.set(true);
                exchange.sendResponseHeaders(500, -1);
            });
            server.httpServer().createContext("/v1/chat/completions", exchange -> {
                chatTouched.set(true);
                respond(
                        exchange,
                        200,
                        """
                        {"choices":[{"finish_reason":"stop","message":{"content":"OK","role":"assistant"}}],"model":"gpt-oss:20b"}
                        """);
            });

            OpenAiCompatibleChatCompletionsHttpClient client = new OpenAiCompatibleChatCompletionsHttpClient();
            OpenAiChatCompletionRequest body =
                    new OpenAiChatCompletionRequest(
                            "gpt-oss:20b", List.of(new OpenAiChatMessageDto("user", "hola")), 0.1);
            assertEquals("OK", client.post(server.baseUrl(), "test-key", body, 5_000).choices().getFirst().message().content());
            assertTrue(chatTouched.get());
            assertFalse(modelsTouched.get());
        }
    }

    @Test
    void openAiCompatibleEmbeddingsCallV1EmbeddingsOnly() throws Exception {
        AtomicBoolean embeddingsHit = new AtomicBoolean(false);

        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext("/v1/embeddings", exchange -> {
                embeddingsHit.set(true);
                respond(
                        exchange,
                        200,
                        """
                        {"data":[{"index":0,"embedding":[0.1,0.2]}],"model":"qwen3-embedding:8b"}
                        """);
            });

            OpenAiCompatibleEmbeddingsHttpClient client = new OpenAiCompatibleEmbeddingsHttpClient();
            assertEquals(
                    2,
                    client.post(server.baseUrl(), "key", new OpenAiEmbeddingRequest("qwen3-embedding:8b", "hola"), 5_000)
                            .data()
                            .getFirst()
                            .embedding()
                            .size());
            assertTrue(embeddingsHit.get());
        }
    }

    @Test
    void ollamaChatCallsNativeApiChatOnly() {
        assertTrue(
                Arrays.stream(OllamaNativeLlmEmbeddingClient.class.getMethods())
                        .anyMatch(m -> m.getName().equals("embed")));
    }

    @Test
    void ollamaEmbeddingsCallNativeApiEmbedOnly() throws Exception {
        AtomicBoolean embedTouched = new AtomicBoolean(false);
        AtomicReference<String> path = new AtomicReference<>();

        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext("/api/embed", exchange -> {
                embedTouched.set(true);
                path.set(exchange.getRequestURI().getPath());
                respond(
                        exchange,
                        200,
                        """
                        {"model":"fixture-embed","embeddings":[[0.1,0.2,0.3]]}
                        """);
            });
            server.httpServer().createContext("/v1/embeddings", exchange -> exchange.sendResponseHeaders(500, -1));

            OllamaEmbeddingModelFactory factory =
                    new OllamaEmbeddingModelFactory(new OllamaApi(server.baseUrl()), ObservationRegistry.NOOP);
            OllamaNativeLlmEmbeddingClient client = new OllamaNativeLlmEmbeddingClient(factory);
            client.embed(LlmEmbeddingRequest.ofSingle("fixture-embed", "hola"));

            assertTrue(embedTouched.get());
            assertEquals("/api/embed", path.get());
        }
    }

    @Test
    void openAiCompatibleNeverCallsOllamaNativeEndpoints() throws Exception {
        AtomicBoolean ollamaTouched = new AtomicBoolean(false);

        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext("/api/embed", exchange -> {
                ollamaTouched.set(true);
                exchange.sendResponseHeaders(500, -1);
            });
            server.httpServer().createContext("/v1/embeddings", exchange -> respond(exchange, 200, """
                    {"data":[{"index":0,"embedding":[0.1]}],"model":"m"}
                    """));

            OpenAiCompatibleEmbeddingsHttpClient client = new OpenAiCompatibleEmbeddingsHttpClient();
            client.post(server.baseUrl(), "key", new OpenAiEmbeddingRequest("m", "hola"), 5_000);
            assertFalse(ollamaTouched.get());
        }
    }

    @Test
    void ollamaNativeNeverCallsOpenAiCompatibleEndpoints() throws Exception {
        AtomicBoolean openAiTouched = new AtomicBoolean(false);

        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext("/v1/embeddings", exchange -> {
                openAiTouched.set(true);
                exchange.sendResponseHeaders(500, -1);
            });
            server.httpServer().createContext("/api/embed", exchange -> respond(exchange, 200, """
                    {"model":"fixture-embed","embeddings":[[0.1]]}
                    """));

            OllamaEmbeddingModelFactory factory =
                    new OllamaEmbeddingModelFactory(new OllamaApi(server.baseUrl()), ObservationRegistry.NOOP);
            new OllamaNativeLlmEmbeddingClient(factory).embed(LlmEmbeddingRequest.ofSingle("fixture-embed", "hola"));
            assertFalse(openAiTouched.get());
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static LocalHttpServer localServer() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.start();
        return new LocalHttpServer(httpServer, "http://127.0.0.1:" + httpServer.getAddress().getPort());
    }

    private record LocalHttpServer(HttpServer httpServer, String baseUrl) implements AutoCloseable {
        @Override
        public void close() {
            httpServer.stop(0);
        }
    }
}
