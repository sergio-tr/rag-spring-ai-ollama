package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.service.postretrieval.PostRetrievalProcessor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TracedPostRetrievalProcessor}.
 */
class TracedPostRetrievalProcessorTest {

    private PostRetrievalProcessor delegate;
    private ObservabilitySupport observability;
    private TracedPostRetrievalProcessor traced;

    @BeforeEach
    void setUp() {
        delegate = mock(PostRetrievalProcessor.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedPostRetrievalProcessor(delegate, observability);
    }

    @Test
    void process_delegatesToUnderlyingProcessor() {
        List<Document> docs = List.of(new Document("id", "text", Map.of()));
        when(delegate.process(docs, "q")).thenReturn(docs);

        assertEquals(docs, traced.process(docs, "q"));
        verify(delegate).process(docs, "q");
    }
}
