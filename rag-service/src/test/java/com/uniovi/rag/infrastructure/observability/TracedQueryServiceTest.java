package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.chat.QueryResponse;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.query.QueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedQueryService}.
 */
class TracedQueryServiceTest {

    @Test
    void generateResponse_withoutObservability_delegatesDirectly() {
        QueryService delegate = mock(QueryService.class);
        QueryResponse resp = QueryResponse.fromLLM("ok", QueryType.BOOLEAN_QUERY);
        when(delegate.generateResponse("q", "m")).thenReturn(resp);

        TracedQueryService sut = new TracedQueryService(delegate, null);

        assertThat(sut.generateResponse("q", "m")).isSameAs(resp);
        verify(delegate).generateResponse("q", "m");
    }

    @Test
    void generateResponse_withObservability_recordsMetricsAndDelegates() {
        QueryService delegate = mock(QueryService.class);
        ObservabilitySupport obs = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        QueryResponse resp = QueryResponse.fromLLM("ok", QueryType.BOOLEAN_QUERY);
        when(delegate.generateResponse("q", "m")).thenReturn(resp);

        TracedQueryService sut = new TracedQueryService(delegate, obs);

        assertThat(sut.generateResponse("q", "m")).isSameAs(resp);
        verify(delegate).generateResponse("q", "m");

        assertThat(obs.getMeterRegistry().find("rag.query.calls").counter()).isNotNull();
        assertThat(obs.getMeterRegistry().find("rag.query.calls").counter().count()).isEqualTo(1.0);

        assertThat(obs.getMeterRegistry().find("rag.query.generate").timer()).isNotNull();
        assertThat(obs.getMeterRegistry().find("rag.query.generate").timer().count()).isEqualTo(1);
    }

    @Test
    void generateResponse_truncatesVeryLongQuestionsForSpanAttributes() {
        QueryService delegate = mock(QueryService.class);
        ObservabilitySupport obs = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        when(delegate.generateResponse(any(), isNull())).thenAnswer(inv -> QueryResponse.fromLLM("x", QueryType.BOOLEAN_QUERY));

        TracedQueryService sut = new TracedQueryService(delegate, obs);

        String big = "x".repeat(3000);
        sut.generateResponse(big, null);

        verify(delegate).generateResponse(eq(big), isNull());
        assertThat(obs.getMeterRegistry().find("rag.query.calls").counter().count()).isEqualTo(1.0);
    }
}
