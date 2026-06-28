package com.uniovi.rag.infrastructure.classifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class ClassifierHttpErrorSupportTest {

    @Test
    void uvicornProtocolRejection_mapsToUnavailable() {
        ClassifierCallException ex =
                ClassifierHttpErrorSupport.fromHttpStatus(
                        400, "Invalid HTTP request received.", "http://classifier-service:8000/classify", null);

        assertEquals(ClassifierCallException.Kind.UNAVAILABLE, ex.kind());
        assertTrue(ex.getMessage().contains("protocol error"));
    }

    @Test
    void validationJson400_mapsToInvalidRequest() {
        String body =
                "{\"success\":false,\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"Query must not be empty\"}}";
        ClassifierCallException ex =
                ClassifierHttpErrorSupport.fromHttpStatus(400, body, "http://classifier-service:8000/classify", null);

        assertEquals(ClassifierCallException.Kind.INVALID_REQUEST, ex.kind());
    }

    @Test
    void timeoutTransport_mapsToTimeout() {
        ClassifierCallException ex =
                ClassifierHttpErrorSupport.fromTransport(
                        "http://classifier-service:8000/classify",
                        new ResourceAccessException("Read timed out"));

        assertEquals(ClassifierCallException.Kind.TIMEOUT, ex.kind());
    }
}
