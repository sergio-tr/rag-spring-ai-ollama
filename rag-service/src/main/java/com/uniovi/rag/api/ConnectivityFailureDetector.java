package com.uniovi.rag.api;

import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;

/**
 * Detects transport-level failures to LLM / HTTP backends (Ollama, etc.) from exception messages.
 * <p>
 * For explicit HTTP connectivity and model presence checks (and triggering {@code /api/pull}),
 * use {@link OllamaConnectivityChecker}.
 */
public final class ConnectivityFailureDetector {

    private ConnectivityFailureDetector() {
    }

    /**
     * @return true if the failure chain indicates the remote service was not reachable (not an application-level 4xx/5xx body).
     */
    public static boolean isConnectivityFailure(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 32) {
            if (cur instanceof ResourceAccessException) {
                return true;
            }
            if (cur instanceof ConnectException) {
                return true;
            }
            if (cur instanceof UnknownHostException) {
                return true;
            }
            if (cur instanceof SocketTimeoutException) {
                return true;
            }
            if (cur instanceof ClosedChannelException) {
                return true;
            }
            if (cur instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            if (cur instanceof UncheckedIOException uio) {
                if (uio.getCause() instanceof IOException) {
                    return true;
                }
            }
            if (cur instanceof IOException && !(cur instanceof java.io.FileNotFoundException)) {
                // Generic I/O to remote — often broken pipe / reset
                String m = cur.getMessage();
                if (m != null && (m.contains("Connection reset") || m.contains("Broken pipe"))) {
                    return true;
                }
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("connection refused")
                        || lower.contains("failed to connect")
                        || lower.contains("timed out")
                        || lower.contains("i/o error on post request")
                        || lower.contains("i/o error on get request")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * Ollama returns HTTP 404 with a body like {@code model "x" not found, try pulling it first}
     * when the chat or embedding model is not installed. This is not a transport failure but must
     * not trigger a second LLM call for "error apology" (which would fail the same way).
     */
    public static boolean isOllamaModelMissingFailure(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 32) {
            String msg = cur.getMessage();
            if (msg != null && isOllamaModelMissingMessage(msg)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static boolean isOllamaModelMissingMessage(String msg) {
        String u = msg.toLowerCase();
        if (!u.contains("model")) {
            return false;
        }
        if (u.contains("try pulling")) {
            return true;
        }
        return u.contains("not found") && (u.contains("404") || u.contains("\"error\""));
    }
}
