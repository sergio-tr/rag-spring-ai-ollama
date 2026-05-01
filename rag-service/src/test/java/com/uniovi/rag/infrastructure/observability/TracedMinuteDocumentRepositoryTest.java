package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.domain.model.AddResult;
import com.uniovi.rag.domain.model.Minute;
import com.uniovi.rag.infrastructure.persistence.MinuteDocumentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedMinuteDocumentRepository}.
 */
class TracedMinuteDocumentRepositoryTest {

    private MinuteDocumentRepository delegate;
    private ObservabilitySupport observability;
    private TracedMinuteDocumentRepository traced;

    @BeforeEach
    void setUp() {
        delegate = mock(MinuteDocumentRepository.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedMinuteDocumentRepository(delegate, observability);
    }

    @Test
    void addMinute_delegatesAndReturnsResult() {
        Minute minute = new Minute("id-1", "f", null, null, null, null, null, null, null, 0, null, null, null, null, null);
        when(delegate.addMinute(any(Minute.class))).thenReturn(AddResult.ADDED);

        assertEquals(AddResult.ADDED, traced.addMinute(minute));
        verify(delegate).addMinute(minute);
    }

    @Test
    void addMinute_whenObservabilityNull_delegatesOnly() {
        TracedMinuteDocumentRepository noObs = new TracedMinuteDocumentRepository(delegate, null);
        Minute minute = new Minute("id-1", "f", null, null, null, null, null, null, null, 0, null, null, null, null, null);
        when(delegate.addMinute(any(Minute.class))).thenReturn(AddResult.ALREADY_EXISTS);

        assertEquals(AddResult.ALREADY_EXISTS, noObs.addMinute(minute));
        verify(delegate).addMinute(minute);
    }

    @Test
    void deleteById_delegates() {
        when(delegate.deleteById("doc-1")).thenReturn(3);
        assertEquals(3, traced.deleteById("doc-1"));
        verify(delegate).deleteById("doc-1");
    }

    @Test
    void deleteById_whenObservabilityNull_delegatesOnly() {
        TracedMinuteDocumentRepository noObs = new TracedMinuteDocumentRepository(delegate, null);
        when(delegate.deleteById("x")).thenReturn(1);
        assertEquals(1, noObs.deleteById("x"));
        verify(delegate).deleteById("x");
    }

    @Test
    void hasDocumentWithId_delegates() {
        when(delegate.hasDocumentWithId("id")).thenReturn(true);
        assertTrue(traced.hasDocumentWithId("id"));
        when(delegate.hasDocumentWithId("id")).thenReturn(false);
        assertFalse(traced.hasDocumentWithId("id"));
        verify(delegate, times(2)).hasDocumentWithId("id");
    }

    @Test
    void hasDocumentWithId_whenObservabilityNull_delegatesOnly() {
        TracedMinuteDocumentRepository noObs = new TracedMinuteDocumentRepository(delegate, null);
        when(delegate.hasDocumentWithId("x")).thenReturn(true);
        assertTrue(noObs.hasDocumentWithId("x"));
        verify(delegate).hasDocumentWithId("x");
    }
}
