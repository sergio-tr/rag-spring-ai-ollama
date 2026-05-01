package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.service.query.ResponseValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedResponseValidator}.
 */
class TracedResponseValidatorTest {

    private ResponseValidator delegate;
    private ObservabilitySupport observability;
    private TracedResponseValidator traced;

    @BeforeEach
    void setUp() {
        delegate = mock(ResponseValidator.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedResponseValidator(delegate, observability);
    }

    @Test
    void isValidResponse_delegates() {
        when(delegate.isValidResponse("r", "ctx")).thenReturn(true);
        assertTrue(traced.isValidResponse("r", "ctx"));
        when(delegate.isValidResponse("r", "ctx")).thenReturn(false);
        assertFalse(traced.isValidResponse("r", "ctx"));
        verify(delegate, times(2)).isValidResponse("r", "ctx");
    }

    @Test
    void cleanResponse_delegates() {
        when(delegate.cleanResponse("raw")).thenReturn("cleaned");
        assertEquals("cleaned", traced.cleanResponse("raw"));
        verify(delegate).cleanResponse("raw");
    }

    @Test
    void validateAndClean_delegates() {
        when(delegate.validateAndClean("r", "ctx")).thenReturn("valid");
        assertEquals("valid", traced.validateAndClean("r", "ctx"));
        verify(delegate).validateAndClean("r", "ctx");
    }
}
