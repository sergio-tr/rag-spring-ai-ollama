package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleChatCompletionsHttpClientTest {

    private static final String SUCCESS_BODY =
            """
            {
              "choices": [{"finish_reason": "stop", "message": {"content": "OK", "role": "assistant"}}],
              "model": "gpt-oss:20b"
            }
            """;

    @Test
    void post_onlyCallsChatCompletions_neverModelsEndpoint() throws Exception {
        AtomicBoolean modelsTouched = new AtomicBoolean(false);

        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext(
                    "/v1/models",
                    exchange -> {
                        modelsTouched.set(true);
                        exchange.sendResponseHeaders(500, -1);
                    });
            server.httpServer().createContext(
                    "/v1/chat/completions",
                    exchange -> respond(exchange, 200, SUCCESS_BODY));

            OpenAiCompatibleChatCompletionsHttpClient client = new OpenAiCompatibleChatCompletionsHttpClient();
            OpenAiChatCompletionRequest body =
                    new OpenAiChatCompletionRequest(
                            "gpt-oss:20b",
                            List.of(new OpenAiChatMessageDto("user", "hola")),
                            0.1);
            OpenAiChatCompletionResponse response =
                    client.post(server.baseUrl(), "test-key", body, 5_000);

            assertEquals("OK", response.choices().getFirst().message().content());
            assertFalse(modelsTouched.get(), "client must not call /v1/models");
        }
    }

    @Test
    void post_forbidden403_mapsToUnauthorized() throws Exception {
        try (LocalHttpServer server = localServer()) {
            server.httpServer().createContext("/v1/chat/completions", exchange -> respond(exchange, 403, "forbidden"));

            OpenAiCompatibleChatCompletionsHttpClient client = new OpenAiCompatibleChatCompletionsHttpClient();
            OpenAiCompatibleLlmException ex =
                    assertThrows(
                            OpenAiCompatibleLlmException.class,
                            () ->
                                    client.post(
                                            server.baseUrl(),
                                            "bad-key",
                                            new OpenAiChatCompletionRequest(
                                                    "gpt-oss:20b",
                                                    List.of(new OpenAiChatMessageDto("user", "hola")),
                                                    null),
                                            5_000));
            assertEquals(OpenAiCompatibleLlmFailureKind.UNAUTHORIZED, ex.kind());
            assertTrue(ex.getMessage().contains("403"));
        }
    }

    @Test
    void post_slowServer_mapsToTimeout() throws Exception {
        try (LocalHttpServer server = localServer()) {
            server.httpServer()
                    .createContext(
                            "/v1/chat/completions",
                            exchange -> {
                                try {
                                    Thread.sleep(1_500);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                respond(exchange, 200, SUCCESS_BODY);
                            });

            OpenAiCompatibleChatCompletionsHttpClient client = new OpenAiCompatibleChatCompletionsHttpClient();
            OpenAiCompatibleLlmException ex =
                    assertThrows(
                            OpenAiCompatibleLlmException.class,
                            () ->
                                    client.post(
                                            server.baseUrl(),
                                            "test-key",
                                            new OpenAiChatCompletionRequest(
                                                    "gpt-oss:20b",
                                                    List.of(new OpenAiChatMessageDto("user", "hola")),
                                                    null),
                                            200));
            assertEquals(OpenAiCompatibleLlmFailureKind.TIMEOUT, ex.kind());
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
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        return new LocalHttpServer(httpServer, baseUrl);
    }

    private record LocalHttpServer(HttpServer httpServer, String baseUrl) implements AutoCloseable {
        @Override
        public void close() {
            httpServer.stop(0);
        }
    }
}
