package com.uniovi.rag.infrastructure.llm.ollama;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OllamaApiClientTest {

    private static RagHealthProperties healthProps() {
        RagHealthProperties p = new RagHealthProperties();
        p.setConnectTimeoutMs(3000);
        p.setReadTimeoutMs(3000);
        return p;
    }

    @Test
    void listModelNames_success() throws Exception {
        try (AutoCloseableServer s = serverWithTags(200, "{\"models\":[{\"name\":\"a:1\"},{\"name\":\"b:2\"}]}")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            Set<String> names = client.listModelNames();
            assertEquals(2, names.size());
            assertTrue(names.contains("a:1"));
        }
    }

    @Test
    void listModelNames_httpError_throws() throws Exception {
        try (AutoCloseableServer s = serverWithTags(500, "err")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            assertThrows(IOException.class, client::listModelNames);
        }
    }

    @Test
    void ping_success() throws Exception {
        try (AutoCloseableServer s = serverWithTags(200, "{}")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            assertTrue(client.ping());
        }
    }

    @Test
    void ping_httpError_returnsFalse() throws Exception {
        try (AutoCloseableServer s = serverWithTags(503, "down")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            assertFalse(client.ping());
        }
    }

    @Test
    void pullModel_success_emptyBody() throws Exception {
        try (AutoCloseableServer s = serverWithPull(200, "")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            assertDoesNotThrow(() -> client.pullModel("gemma:1", 10_000L));
        }
    }

    @Test
    void pullModel_success_jsonNoError() throws Exception {
        try (AutoCloseableServer s = serverWithPull(200, "{\"status\":\"success\"}")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            assertDoesNotThrow(() -> client.pullModel("gemma:1", 10_000L));
        }
    }

    @Test
    void pullModel_jsonErrorField_throws() throws Exception {
        try (AutoCloseableServer s = serverWithPull(200, "{\"error\":\"pull failed\"}")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            IOException ex = assertThrows(IOException.class, () -> client.pullModel("x", 10_000L));
            assertTrue(ex.getMessage().contains("pull failed"));
        }
    }

    @Test
    void pullModel_nonJsonBody_ok() throws Exception {
        try (AutoCloseableServer s = serverWithPull(200, "not json but ok")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            assertDoesNotThrow(() -> client.pullModel("x", 10_000L));
        }
    }

    @Test
    void pullModel_httpError_throws() throws Exception {
        try (AutoCloseableServer s = serverWithPull(500, "bad")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            assertThrows(IOException.class, () -> client.pullModel("m", 5_000L));
        }
    }

    @Test
    void probeEmbeddingDetailed_usesModernEmbedEndpoint() throws Exception {
        try (AutoCloseableServer s = serverWithEmbed(200, "{\"embeddings\":[[0.1,0.2]]}")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            var result = client.probeEmbeddingDetailed("bge-m3:latest", "ping", 5_000L);
            assertTrue(result.ok());
        }
    }

    @Test
    void probeEmbeddingDetailed_fallsBackToLegacyEmbeddingsEndpoint() throws Exception {
        try (AutoCloseableServer s = serverWithEmbedAndLegacy(404, "{\"embedding\":[0.1,0.2]}")) {
            OllamaApiClient client = new OllamaApiClient(s.baseUrl(), healthProps());
            var result = client.probeEmbeddingDetailed("e:latest", "ping", 5_000L);
            assertTrue(result.ok());
        }
    }

    /** Small helper: tags + pull on same server. */
    private static final class AutoCloseableServer implements AutoCloseable {
        private final HttpServer server;
        private final String base;

        AutoCloseableServer(HttpServer server, String base) {
            this.server = server;
            this.base = base;
        }

        String baseUrl() {
            return base;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private AutoCloseableServer serverWithTags(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/tags", ex -> write(ex, status, body));
        server.start();
        return new AutoCloseableServer(server, "http://127.0.0.1:" + port);
    }

    private AutoCloseableServer serverWithPull(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/pull", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            ex.getRequestBody().readAllBytes();
            write(ex, status, body);
        });
        server.start();
        return new AutoCloseableServer(server, "http://127.0.0.1:" + port);
    }

    private AutoCloseableServer serverWithEmbed(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/embed", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            ex.getRequestBody().readAllBytes();
            write(ex, status, body);
        });
        server.start();
        return new AutoCloseableServer(server, "http://127.0.0.1:" + port);
    }

    private AutoCloseableServer serverWithEmbedAndLegacy(int legacyStatus, String legacyBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/embed", ex -> write(ex, 404, "missing"));
        server.createContext("/api/embeddings", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            ex.getRequestBody().readAllBytes();
            write(ex, legacyStatus, legacyBody);
        });
        server.start();
        return new AutoCloseableServer(server, "http://127.0.0.1:" + port);
    }

    private static void write(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
