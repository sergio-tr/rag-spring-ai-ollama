package com.uniovi.rag.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ClassifierHealthIndicatorTest {

    private RagHealthProperties baseProps() {
        RagHealthProperties p = new RagHealthProperties();
        p.setConnectTimeoutMs(3000);
        p.setReadTimeoutMs(3000);
        return p;
    }

    @Test
    void whenClassifierDisabled_skippedUp() {
        RagHealthProperties hp = baseProps();
        hp.setClassifierEnabled(false);
        ClassifierHealthIndicator ind = new ClassifierHealthIndicator(hp, "http://127.0.0.1:1");
        Health h = ind.health();
        assertEquals(Status.UP, h.getStatus());
    }

    @Test
    void whenHttpError_down() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/health", ex -> send(ex, 503, "{}"));
            srv.start();
            RagHealthProperties hp = baseProps();
            ClassifierHealthIndicator ind = new ClassifierHealthIndicator(hp, "http://127.0.0.1:" + port);
            Health h = ind.health();
            assertEquals(Status.DOWN, h.getStatus());
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenModelNotLoaded_andRequired_down() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/health", ex -> send(ex, 200, "{\"model\":\"loading\"}"));
            srv.start();
            RagHealthProperties hp = baseProps();
            hp.setClassifierRequireModelLoaded(true);
            ClassifierHealthIndicator ind = new ClassifierHealthIndicator(hp, "http://127.0.0.1:" + port);
            Health h = ind.health();
            assertEquals(Status.DOWN, h.getStatus());
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenModelNotLoaded_andNotRequired_upWithWarning() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/health", ex -> send(ex, 200, "{\"model\":\"loading\"}"));
            srv.start();
            RagHealthProperties hp = baseProps();
            hp.setClassifierRequireModelLoaded(false);
            ClassifierHealthIndicator ind = new ClassifierHealthIndicator(hp, "http://127.0.0.1:" + port);
            Health h = ind.health();
            assertEquals(Status.UP, h.getStatus());
            assertNotNull(h.getDetails().get("warning"));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenModelLoaded_up() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/health", ex -> send(ex, 200, "{\"model\":\"loaded\"}"));
            srv.start();
            RagHealthProperties hp = baseProps();
            ClassifierHealthIndicator ind = new ClassifierHealthIndicator(hp, "http://127.0.0.1:" + port);
            Health h = ind.health();
            assertEquals(Status.UP, h.getStatus());
            assertEquals("loaded", h.getDetails().get("model"));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void whenHostUnreachable_down() {
        RagHealthProperties hp = baseProps();
        ClassifierHealthIndicator ind = new ClassifierHealthIndicator(hp, "http://127.0.0.1:1");
        Health h = ind.health();
        assertEquals(Status.DOWN, h.getStatus());
    }

    @Test
    void whenBaseUrlHasTrailingSlash_stillWorks() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            int port = srv.getAddress().getPort();
            srv.createContext("/health", ex -> send(ex, 200, "{\"model\":\"loaded\"}"));
            srv.start();
            RagHealthProperties hp = baseProps();
            ClassifierHealthIndicator ind = new ClassifierHealthIndicator(hp, "http://127.0.0.1:" + port + "/");
            Health h = ind.health();
            assertEquals(Status.UP, h.getStatus());
        } finally {
            srv.stop(0);
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
