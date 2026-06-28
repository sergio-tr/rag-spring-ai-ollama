package com.uniovi.rag.infrastructure.health;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OllamaHealthIndicatorTest {

    private RagHealthProperties baseProps() {
        RagHealthProperties p = new RagHealthProperties();
        p.setConnectTimeoutMs(3000);
        p.setReadTimeoutMs(3000);
        return p;
    }

    private LlmProperties ollamaStackProps() {
        LlmProperties p = new LlmProperties();
        p.setDefaultProvider(LlmProvider.OLLAMA_NATIVE);
        return p;
    }

    @Test
    void whenOllamaDisabled_skippedUp() {
        RagHealthProperties hp = baseProps();
        hp.setOllamaEnabled(false);
        OllamaHealthIndicator ind = new OllamaHealthIndicator(hp, ollamaStackProps(), "http://127.0.0.1:1", "c", "e");
        Health h = ind.health();
        assertEquals(Status.UP, h.getStatus());
        assertEquals("skipped", h.getDetails().get("check"));
    }

    @Test
    void whenTagsHttpError_down() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/api/tags", OllamaHealthIndicatorTest::send500);
            srv.start();
            RagHealthProperties hp = baseProps();
            OllamaHealthIndicator ind = new OllamaHealthIndicator(hp, ollamaStackProps(), "http://127.0.0.1:" + port, "c", "e");
            Health h = ind.health();
            assertEquals(Status.DOWN, h.getStatus());
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenVerifyModelsOff_upWithoutModels() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/api/tags", ex -> send(ex, 200, "{\"models\":[]}"));
            srv.start();
            RagHealthProperties hp = baseProps();
            hp.setOllamaVerifyModels(false);
            OllamaHealthIndicator ind = new OllamaHealthIndicator(hp, ollamaStackProps(), "http://127.0.0.1:" + port, "c", "e");
            Health h = ind.health();
            assertEquals(Status.UP, h.getStatus());
            assertEquals(false, h.getDetails().get("modelsVerified"));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenModelsMissing_down() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/api/tags", ex -> send(ex, 200, "{\"models\":[{\"name\":\"only-one\"}]}"));
            srv.start();
            RagHealthProperties hp = baseProps();
            OllamaHealthIndicator ind = new OllamaHealthIndicator(hp, ollamaStackProps(), "http://127.0.0.1:" + port, "need-chat", "need-emb");
            Health h = ind.health();
            assertEquals(Status.DOWN, h.getStatus());
            assertNotNull(h.getDetails().get("missingModels"));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenModelsPresent_up() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            String body = "{\"models\":[{\"name\":\"need-chat\"},{\"name\":\"need-emb\"}]}";
            srv.createContext("/api/tags", ex -> send(ex, 200, body));
            srv.start();
            RagHealthProperties hp = baseProps();
            OllamaHealthIndicator ind = new OllamaHealthIndicator(hp, ollamaStackProps(), "http://127.0.0.1:" + port, "need-chat", "need-emb");
            Health h = ind.health();
            assertEquals(Status.UP, h.getStatus());
            assertEquals("need-chat", h.getDetails().get("chatModel"));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenConnectionFails_down() {
        RagHealthProperties hp = baseProps();
        OllamaHealthIndicator ind = new OllamaHealthIndicator(hp, ollamaStackProps(), "http://127.0.0.1:1", "c", "e");
        Health h = ind.health();
        assertEquals(Status.DOWN, h.getStatus());
    }

    @Test
    void whenOpenAiCompatibleProvider_skipsOllamaTags() {
        RagHealthProperties hp = baseProps();
        LlmProperties llm = new LlmProperties();
        llm.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        OllamaHealthIndicator ind = new OllamaHealthIndicator(hp, llm, "http://127.0.0.1:1", "c", "e");
        Health h = ind.health();
        assertEquals(Status.UP, h.getStatus());
        assertEquals("skipped", h.getDetails().get("check"));
    }

    private static void send500(HttpExchange ex) throws IOException {
        send(ex, 500, "err");
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
