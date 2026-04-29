package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.service.retriever.ContextRetriever;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedContextRetriever}.
 */
class TracedContextRetrieverTest {

    private ContextRetriever delegate;
    private SimpleMeterRegistry meterRegistry;
    private ObservabilitySupport observability;
    private TracedContextRetriever traced;

    @BeforeEach
    void setUp() {
        delegate = mock(ContextRetriever.class);
        meterRegistry = new SimpleMeterRegistry();
        observability = new ObservabilitySupport(new SimpleTracer(), meterRegistry);
        traced = new TracedContextRetriever(delegate, observability);
    }

    @Test
    void retrieve_delegatesAndReturnsDocuments() {
        List<Document> docs = List.of(new Document("1", "c", Map.of()));
        when(delegate.retrieve("q")).thenReturn(docs);

        assertEquals(docs, traced.retrieve("q"));
        verify(delegate).retrieve("q");
    }

    @Test
    void retrieve_incrementsBucketedRetrievalMetric() {
        when(delegate.retrieve("q")).thenReturn(List.of(new Document("1", "a", Map.of())));

        traced.retrieve("q");

        assertEquals(
                1.0,
                meterRegistry.counter("rag_retrieval_documents_total", "operation", "retrieve", "bucket", "1_4").count());
    }

    @Test
    void sizeBucket_mapsRanges() {
        assertEquals("0", TracedContextRetriever.sizeBucket(0));
        assertEquals("1_4", TracedContextRetriever.sizeBucket(1));
        assertEquals("5_19", TracedContextRetriever.sizeBucket(5));
        assertEquals("20_plus", TracedContextRetriever.sizeBucket(25));
    }

    @Test
    void retrieveWithMetadataFilters_delegates() {
        List<Document> docs = List.of(new Document("1", "c", Map.of()));
        JSONObject entities = new JSONObject();
        when(delegate.retrieveWithMetadataFilters("q", entities)).thenReturn(docs);

        assertEquals(docs, traced.retrieveWithMetadataFilters("q", entities));
        verify(delegate).retrieveWithMetadataFilters("q", entities);
    }

    @Test
    void createContext_delegates() {
        List<Document> docs = List.of(new Document("1", "c", Map.of()));
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
