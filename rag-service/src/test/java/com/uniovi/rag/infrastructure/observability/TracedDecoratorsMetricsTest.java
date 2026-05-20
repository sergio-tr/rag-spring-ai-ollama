package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.query.CandidateResponse;
import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.model.RankerResult;
import com.uniovi.rag.application.service.runtime.query.analyser.QueryAnalyser;
import com.uniovi.rag.infrastructure.classifier.QueryClassifier;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.query.expand.QueryExpander;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import com.uniovi.rag.application.service.runtime.ranking.ResponseRanker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class TracedDecoratorsMetricsTest {

    private ObservabilitySupport observability;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observability = new ObservabilitySupport(new SimpleTracer(), meterRegistry);
    }

    @Test
    void tracedQueryClassifier_recordsCounterAndTimer() {
        QueryClassifier delegate = mock(QueryClassifier.class);
        when(delegate.classify("q")).thenReturn(QueryType.COUNT_DOCUMENTS);

        TracedQueryClassifier traced = new TracedQueryClassifier(delegate, observability);
        QueryType result = traced.classify("q");

        assertEquals(QueryType.COUNT_DOCUMENTS, result);
        verify(delegate).classify("q");

        assertEquals(1.0, meterRegistry.find("rag.classifier.calls")
                .tag("operation", "classify")
                .counter()
                .count());
        assertTrue(meterRegistry.find("rag.classifier.classify").timer().count() >= 1);
    }

    @Test
    void tracedQueryExpander_recordsCounterAndTimer() {
        QueryExpander delegate = mock(QueryExpander.class);
        when(delegate.expand("q")).thenReturn("expanded");

        String expanderTag = delegate.getClass().getSimpleName();
        TracedQueryExpander traced = new TracedQueryExpander(delegate, observability);
        String result = traced.expand("q");

        assertEquals("expanded", result);
        verify(delegate).expand("q");

        assertEquals(1.0, meterRegistry.find("rag.expander.calls")
                .tag("expander", expanderTag)
                .counter()
                .count());
        assertTrue(meterRegistry.find("rag.expander.expand").timer().count() >= 1);
    }

    @Test
    void tracedQueryAnalyser_recordsCounterAndTimer() throws JSONException {
        QueryAnalyser delegate = mock(QueryAnalyser.class);
        JSONObject analysis = new JSONObject().put("k", "v");
        when(delegate.analyse("q")).thenReturn(analysis);

        String analyserTag = delegate.getClass().getSimpleName();
        TracedQueryAnalyser traced = new TracedQueryAnalyser(delegate, observability);
        JSONObject result = traced.analyse("q");

        assertEquals(analysis, result);
        verify(delegate).analyse("q");

        assertEquals(1.0, meterRegistry.find("rag.analyser.calls")
                .tag("analyser", analyserTag)
                .counter()
                .count());
        assertTrue(meterRegistry.find("rag.analyser.analyse").timer().count() >= 1);
    }

    @Test
    void tracedQueryExecutionService_recordsCounterAndTimer() {
        QueryExecutionService delegate = mock(QueryExecutionService.class);
        QueryResponse expected = QueryResponse.fromLLM("answer", QueryType.COUNT_DOCUMENTS);
        when(delegate.generateResponse(eq("question"), isNull())).thenReturn(expected);

        TracedQueryService traced = new TracedQueryService(delegate, observability);
        QueryResponse result = traced.generateResponse("question");

        assertSame(expected, result);
        verify(delegate).generateResponse(eq("question"), isNull());

        assertEquals(1.0, meterRegistry.find("rag.query.calls")
                .tag("operation", "generateResponse")
                .counter()
                .count());
        assertTrue(meterRegistry.find("rag.query.generate").timer().count() >= 1);
    }

    @Test
    void tracedResponseRanker_recordsCounterAndTimer() {
        ResponseRanker delegate = mock(ResponseRanker.class);
        RankerResult expected = RankerResult.of("best", 0);
        when(delegate.selectBest(eq("q"), eq("ctx"), anyList())).thenReturn(expected);

        String rankerTag = delegate.getClass().getSimpleName();
        TracedResponseRanker traced = new TracedResponseRanker(delegate, observability);
        RankerResult result = traced.selectBest("q", "ctx", List.of(new CandidateResponse("t", null)));

        assertEquals(expected, result);
        verify(delegate).selectBest(eq("q"), eq("ctx"), anyList());

        assertEquals(1.0, meterRegistry.find("rag.ranker.calls")
                .tag("ranker", rankerTag)
                .counter()
                .count());
        assertTrue(meterRegistry.find("rag.ranker.selectBest").timer().count() >= 1);
    }

    @Test
    void tracedDocumentContentExtractor_recordsCounter() {
        DocumentContentExtractor delegate = mock(DocumentContentExtractor.class);
        when(delegate.extractAttendeeCount("content")).thenReturn(3);

        TracedDocumentContentExtractor traced = new TracedDocumentContentExtractor(delegate, observability);
        int result = traced.extractAttendeeCount("content");

        assertEquals(3, result);
        verify(delegate).extractAttendeeCount("content");

        assertEquals(1.0, meterRegistry.find("rag.extraction.calls")
                .tag("operation", "extractAttendeeCount")
                .counter()
                .count());
    }
}

