package com.uniovi.rag.interfaces.rest.support;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectivityFailureDetectorMoreTest {

    @Test
    void detectsUnknownHost() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(new UnknownHostException("x")));
    }

    @Test
    void detectsSocketTimeout() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(new SocketTimeoutException()));
    }

    @Test
    void detectsClosedChannel() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(new ClosedChannelException()));
    }

    @Test
    void detectsHttpTimeoutException() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(
                new java.net.http.HttpTimeoutException("timeout")));
    }

    @Test
    void detectsUncheckedIOException() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(
                new UncheckedIOException(new IOException("io"))));
    }

    @Test
    void genericIOException_withResetMessage() {
        IOException io = new IOException("Connection reset by peer");
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(io));
    }

    @Test
    void genericIOException_withBrokenPipe() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(
                new IOException("Broken pipe")));
    }

    @Test
    void fileNotFoundException_notTreatedAsConnectivity() {
        assertFalse(ConnectivityFailureDetector.isConnectivityFailure(new FileNotFoundException("x")));
    }

    @Test
    void messagePattern_connectionRefused() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(
                new RuntimeException("connection refused")));
    }

    @Test
    void messagePattern_connectionRefusedInCauseChain() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(
                new IllegalStateException("wrap", new Exception("Connection refused"))));
    }

    @Test
    void depthLimit_stopsEventually() {
        Throwable t = new Exception("leaf");
        for (int i = 0; i < 40; i++) {
            t = new Exception("wrap-" + i, t);
        }
        assertFalse(ConnectivityFailureDetector.isConnectivityFailure(t));
    }

    @Test
    void detectsOllamaEmbeddingModel404_tryPulling() {
        RuntimeException ex = new RuntimeException(
                "[404] Not Found - {\"error\":\"model \\\"mxbai-embed-large\\\" not found, try pulling it first\"}");
        assertTrue(ConnectivityFailureDetector.isOllamaModelMissingFailure(ex));
    }

    @Test
    void detectsOllamaChatModel404() {
        RuntimeException ex = new RuntimeException("[404] Not Found - {\"error\":\"model 'gemma3:4b' not found\"}");
        assertTrue(ConnectivityFailureDetector.isOllamaModelMissingFailure(ex));
    }

    @Test
    void ollamaModelMissing_nestedCause() {
        Throwable inner = new RuntimeException("[404] Not Found - {\"error\":\"model \\\"x\\\" not found, try pulling it first\"}");
        assertTrue(ConnectivityFailureDetector.isOllamaModelMissingFailure(new IllegalStateException("wrap", inner)));
    }

    @Test
    void genericNotFound_notDetectedAsOllamaModel() {
        assertFalse(ConnectivityFailureDetector.isOllamaModelMissingFailure(new RuntimeException("404 page not found")));
    }
}
