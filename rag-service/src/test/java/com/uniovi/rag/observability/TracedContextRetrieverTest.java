package com.uniovi.rag.observability;

import com.uniovi.rag.service.retriever.ContextRetriever;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedContextRetriever}.
 */
class TracedContextRetrieverTest {

    private ContextRetriever delegate;
    private ObservabilitySupport observability;
    private TracedContextRetriever traced;

    @BeforeEach
    void setUp() {
        delegate = mock(ContextRetriever.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedContextRetriever(delegate, observability);
    }

    @Test
    void retrieve_delegatesAndReturnsDocuments() {
        List<Document> docs = List.of(new Document("1", "c", java.util.Map.of()));
        when(delegate.retrieve("q")).thenReturn(docs);

        assertEquals(docs, traced.retrieve("q"));
        verify(delegate).retrieve("q");
    }

    @Test
    void retrieveWithMetadataFilters_delegates() {
        List<Document> docs = List.of(new Document("1", "c", java.util.Map.of()));
        JSONObject entities = new JSONObject();
        when(delegate.retrieveWithMetadataFilters("q", entities)).thenReturn(docs);

        assertEquals(docs, traced.retrieveWithMetadataFilters("q", entities));
        verify(delegate).retrieveWithMetadataFilters("q", entities);
    }

    @Test
    void createContext_delegates() {
        List<Document> docs = List.of(new Document("1", "c", java.util.Map.of()));
        when(delegate.createContext(any(), eq("q"), any())).thenReturn("context");

        assertEquals("context", traced.createContext(docs, "q", null));
        verify(delegate).createContext(docs, "q", null);
    }

    @Test
    void getTopK_setTopK_delegate() {
        when(delegate.getTopK()).thenReturn(5);
        assertEquals(5, traced.getTopK());
        doNothing().when(delegate).setTopK(10);
        traced.setTopK(10);
        verify(delegate).setTopK(10);
    }

    @Test
    void getSimilarityThreshold_setSimilarityThreshold_delegate() {
        when(delegate.getSimilarityThreshold()).thenReturn(0.7);
        assertEquals(0.7, traced.getSimilarityThreshold());
        doNothing().when(delegate).setSimilarityThreshold(0.8);
        traced.setSimilarityThreshold(0.8);
        verify(delegate).setSimilarityThreshold(0.8);
    }

    @Test
    void restoreDefaultSettings_delegates() {
        doNothing().when(delegate).restoreDefaultSettings();
        traced.restoreDefaultSettings();
        verify(delegate).restoreDefaultSettings();
    }
}
