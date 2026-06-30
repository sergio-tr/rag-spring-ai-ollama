package com.uniovi.rag.infrastructure.llm.openaicompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleLlmChatClientTest {

    private static final String SUCCESS_BODY =
            """
            {
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": {
                    "content": "OK",
                    "role": "assistant"
                  }
                }
              ],
              "usage": {
                "completion_tokens": 38,
                "prompt_tokens": 76,
                "total_tokens": 114
              },
              "model": "gpt-oss:20b"
            }
            """;

    @Test
    void chat_postsToV1ChatCompletionsWithBearerAuth() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        try (AutoCloseableServer server =
                server(
                        200,
                        SUCCESS_BODY,
                        exchange -> {
                            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                            path.set(exchange.getRequestURI().getPath());
                            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                        })) {
            OpenAiCompatibleLlmChatClient client = client(server.baseUrl(), "secret-key-value", "gpt-oss:20b");
            LlmChatResponse response =
                    client.chat(
                            LlmChatRequest.of(
                                    "gpt-oss:20b",
                                    "responde siempre en español",
                                    "Responde solo con OK",
                                    0.1,
                                    5_000,
                                    Map.of()));

            assertEquals("OK", response.content());
            assertEquals("gpt-oss:20b", response.model());
            assertEquals("stop", response.finishReason());
            assertEquals(76, response.usage().promptTokens());
            assertEquals(LlmProvider.OPENAI_COMPATIBLE, client.provider());
            assertEquals("Bearer secret-key-value", authorization.get());
            assertEquals("/v1/chat/completions", path.get());
            assertTrue(requestBody.get().contains("\"model\":\"gpt-oss:20b\""));
            assertTrue(requestBody.get().contains("\"role\":\"system\""));
            assertFalse(requestBody.get().contains("secret-key-value"));
        }
    }

    @Test
    void healthCheckViaChatCompletion_usesConfiguredModel() throws Exception {
        try (AutoCloseableServer server = server(200, SUCCESS_BODY, exchange -> {})) {
            OpenAiCompatibleLlmChatClient client = client(server.baseUrl(), "secret-key-value", "gpt-oss:20b");
            assertTrue(client.healthCheckViaChatCompletion());
        }
    }

    @Test
    void chat_unauthorizedMapsToClearError() throws Exception {
        try (AutoCloseableServer server = server(401, "{\"error\":\"invalid key\"}", exchange -> {})) {
            OpenAiCompatibleLlmChatClient client = client(server.baseUrl(), "bad-key", "gpt-oss:20b");
            OpenAiCompatibleLlmException ex =
                    assertThrows(
                            OpenAiCompatibleLlmException.class,
                            () -> client.chat(LlmChatRequest.of("gpt-oss:20b", null, "hola")));
            assertEquals(OpenAiCompatibleLlmFailureKind.UNAUTHORIZED, ex.kind());
        }
    }

    @Test
    void chat_notFoundMapsToEndpointError() throws Exception {
        try (AutoCloseableServer server = server(404, "not found", exchange -> {})) {
            OpenAiCompatibleLlmChatClient client = client(server.baseUrl(), "secret-key-value", "gpt-oss:20b");
            OpenAiCompatibleLlmException ex =
                    assertThrows(
                            OpenAiCompatibleLlmException.class,
                            () -> client.chat(LlmChatRequest.of("gpt-oss:20b", null, "hola")));
            assertEquals(OpenAiCompatibleLlmFailureKind.ENDPOINT_NOT_FOUND, ex.kind());
        }
    }

    @Test
    void chat_emptyChoicesRejected() throws Exception {
        try (AutoCloseableServer server = server(200, "{\"choices\":[]}", exchange -> {})) {
            OpenAiCompatibleLlmChatClient client = client(server.baseUrl(), "secret-key-value", "gpt-oss:20b");
            OpenAiCompatibleLlmException ex =
                    assertThrows(
                            OpenAiCompatibleLlmException.class,
                            () -> client.chat(LlmChatRequest.of("gpt-oss:20b", null, "hola")));
            assertEquals(OpenAiCompatibleLlmFailureKind.INVALID_RESPONSE, ex.kind());
        }
    }

    @Test
    void chat_forbidden403MapsToUnauthorized() throws Exception {
        try (AutoCloseableServer server = server(403, "forbidden", exchange -> {})) {
            OpenAiCompatibleLlmChatClient client = client(server.baseUrl(), "bad-key", "gpt-oss:20b");
            OpenAiCompatibleLlmException ex =
                    assertThrows(
                            OpenAiCompatibleLlmException.class,
                            () -> client.chat(LlmChatRequest.of("gpt-oss:20b", null, "hola")));
            assertEquals(OpenAiCompatibleLlmFailureKind.UNAUTHORIZED, ex.kind());
            assertTrue(ex.getMessage().contains("403"));
        }
    }

    @Test
    void chat_neverCallsModelsEndpoint() throws Exception {
        AtomicBoolean modelsHit = new AtomicBoolean(false);
        try (AutoCloseableServer server =
                serverWithModelsTrap(
                        200,
                        SUCCESS_BODY,
                        modelsHit,
                        exchange -> {})) {
            OpenAiCompatibleLlmChatClient client = client(server.baseUrl(), "secret-key-value", "gpt-oss:20b");
            client.chat(LlmChatRequest.of("gpt-oss:20b", null, "hola"));
            assertFalse(modelsHit.get());
        }
    }

    @Test
    void chat_missingApiKeyFailsWithoutCallingServer() throws Exception {
        try (AutoCloseableServer server = server(200, SUCCESS_BODY, exchange -> {})) {
            OpenAiCompatibleLlmChatClient client =
                    client(server.baseUrl(), null, "gpt-oss:20b", name -> null);
            OpenAiCompatibleLlmException ex =
                    assertThrows(
                            OpenAiCompatibleLlmException.class,
                            () -> client.chat(LlmChatRequest.of("gpt-oss:20b", null, "hola")));
            assertEquals(OpenAiCompatibleLlmFailureKind.MISCONFIGURED, ex.kind());
        }
    }

    private static OpenAiCompatibleLlmChatClient client(
            String baseUrl, String apiKey, String defaultModel, Function<String, String> envReader) {
        LlmProperties properties = new LlmProperties();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl(baseUrl);
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        openAi.setDefaultChatModel(defaultModel);
        openAi.setDefaultTimeoutMs(5_000);
        openAi.setDefaultTemperature(0.1);
        return new OpenAiCompatibleLlmChatClient(
                properties,
                new OpenAiCompatibleApiKeyResolver(envReader != null ? envReader : name -> apiKey),
                new OpenAiCompatibleChatCompletionsHttpClient());
    }

    private static OpenAiCompatibleLlmChatClient client(String baseUrl, String apiKey, String defaultModel) {
        return client(baseUrl, apiKey, defaultModel, name -> apiKey);
    }

    private static AutoCloseableServer server(int status, String body, ExchangeInspector inspector) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(
                "/v1/chat/completions",
                exchange -> {
                    inspector.inspect(exchange);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        return new AutoCloseableServer(httpServer, baseUrl);
    }

    private static AutoCloseableServer serverWithModelsTrap(
            int status, String body, AtomicBoolean modelsHit, ExchangeInspector inspector) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext(
                "/v1/models",
                exchange -> {
                    modelsHit.set(true);
                    exchange.sendResponseHeaders(500, -1);
                });
        httpServer.createContext(
                "/v1/chat/completions",
                exchange -> {
                    inspector.inspect(exchange);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                });
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        return new AutoCloseableServer(httpServer, baseUrl);
    }

    @FunctionalInterface
    private interface ExchangeInspector {
        void inspect(HttpExchange exchange) throws IOException;
    }

    private record AutoCloseableServer(HttpServer server, String baseUrl) implements AutoCloseable {
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
