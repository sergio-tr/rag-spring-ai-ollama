package com.uniovi.rag.infrastructure.llm.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uniovi.rag.application.port.llm.LlmEmbeddingRequest;
import com.uniovi.rag.infrastructure.vector.OllamaEmbeddingModelFactory;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;

class OllamaNativeEmbeddingHttpTest {

    private static final String EMBED_BODY =
            """
            {
              "model": "mxbai-embed-large:latest",
              "embeddings": [[0.1, 0.2, 0.3]]
            }
            """;

    @Test
    void ollamaEmbeddingClientCallsNativeApiEmbed() throws Exception {
        AtomicBoolean embedTouched = new AtomicBoolean(false);
        AtomicReference<String> requestPath = new AtomicReference<>();

        try (LocalHttpServer server = localServer()) {
            server.httpServer()
                    .createContext(
                            "/api/embed",
                            exchange -> {
                                embedTouched.set(true);
                                requestPath.set(exchange.getRequestURI().getPath());
                                respond(exchange, 200, EMBED_BODY);
                            });
            server.httpServer()
                    .createContext(
                            "/v1/embeddings",
                            exchange -> {
                                exchange.sendResponseHeaders(500, -1);
                            });

            OllamaEmbeddingModelFactory factory =
                    new OllamaEmbeddingModelFactory(
                            new OllamaApi(server.baseUrl()), ObservationRegistry.NOOP);
            OllamaNativeLlmEmbeddingClient client = new OllamaNativeLlmEmbeddingClient(factory);

            var response = client.embed(LlmEmbeddingRequest.ofSingle("mxbai-embed-large:latest", "hola"));

            assertEquals(3, response.embeddings().getFirst().length);
            assertTrue(embedTouched.get());
            assertEquals("/api/embed", requestPath.get());
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
