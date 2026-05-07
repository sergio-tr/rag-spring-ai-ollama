package com.uniovi.rag.interfaces.rest.support;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectivityFailureDetectorTest {

    @Test
    void isContextLimitFailure_matchesOllamaMessage() {
        RuntimeException e = new RuntimeException("[400] Bad Request - {\"error\":\"the input length exceeds the context length\"}");
        assertThat(ConnectivityFailureDetector.isContextLimitFailure(e)).isTrue();
    }

    @Test
    void isContextLimitFailure_falseForOtherErrors() {
        RuntimeException e = new RuntimeException("connection refused");
        assertThat(ConnectivityFailureDetector.isContextLimitFailure(e)).isFalse();
    }

    @Test
    void detectsResourceAccessException() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(
                new ResourceAccessException("I/O error", new ConnectException())));
    }

    @Test
    void detectsNestedConnectException() {
        assertTrue(ConnectivityFailureDetector.isConnectivityFailure(
                new RuntimeException("wrap", new ConnectException())));
    }

    @Test
    void ignoresPlainIllegalArgument() {
        assertFalse(ConnectivityFailureDetector.isConnectivityFailure(new IllegalArgumentException("bad")));
    }
}
