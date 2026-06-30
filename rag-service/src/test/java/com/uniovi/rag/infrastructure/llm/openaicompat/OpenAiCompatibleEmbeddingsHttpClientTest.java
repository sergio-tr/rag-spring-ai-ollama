package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingsHttpClientTest {

    private static final String SUCCESS_BODY =
            """
            {
              "data": [{"index": 0, "embedding": [0.1, 0.2, 0.3]}],
              "model": "qwen3-embedding:8b"
            }
            """;

    @Test
    void openAiCompatibleEmbeddingClientCallsV1Embeddings() throws Exception {
        AtomicBoolean embeddingsHit = new AtomicBoolean(false);

        try (LocalHttpServer server = localServer()) {
            server.httpServer()
                    .createContext(
                            "/v1/embeddings",
                            exchange -> {
                                embeddingsHit.set(true);
                                respond(exchange, 200, SUCCESS_BODY);
                            });

            OpenAiCompatibleEmbeddingsHttpClient client = new OpenAiCompatibleEmbeddingsHttpClient();
            OpenAiEmbeddingRequest body = new OpenAiEmbeddingRequest("qwen3-embedding:8b", "hola");
            OpenAiEmbeddingResponse response = client.post(server.baseUrl(), "test-key", body, 5_000);

            assertEquals(3, response.data().getFirst().embedding().size());
            assertEquals(true, embeddingsHit.get());
        }
    }

    @Test
    void post_onlyCallsEmbeddings_neverModelsOrOllamaRoutes() throws Exception {
        AtomicBoolean modelsTouched = new AtomicBoolean(false);
        AtomicBoolean ollamaTouched = new AtomicBoolean(false);

        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext(
                    "/v1/models",
                    exchange -> {
                        modelsTouched.set(true);
                        exchange.sendResponseHeaders(500, -1);
                    });
            server.httpServer().createContext(
                    "/api/embed",
                    exchange -> {
                        ollamaTouched.set(true);
                        exchange.sendResponseHeaders(500, -1);
                    });
            server.httpServer().createContext(
                    "/v1/embeddings",
                    exchange -> respond(exchange, 200, SUCCESS_BODY));

            OpenAiCompatibleEmbeddingsHttpClient client = new OpenAiCompatibleEmbeddingsHttpClient();
            OpenAiEmbeddingRequest body = new OpenAiEmbeddingRequest("qwen3-embedding:8b", List.of("hola"));
            OpenAiEmbeddingResponse response = client.post(server.baseUrl(), "test-key", body, 5_000);

            assertEquals(3, response.data().getFirst().embedding().size());
            assertFalse(modelsTouched.get());
            assertFalse(ollamaTouched.get());
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
        int port = httpServer.getAddress().getPort();
        return new LocalHttpServer(httpServer, "http://127.0.0.1:" + port);
    }

    private record LocalHttpServer(HttpServer httpServer, String baseUrl) implements AutoCloseable {
        @Override
        public void close() {
            httpServer.stop(0);
        }
    }
}
