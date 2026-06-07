package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.reasoning.PostStepOutput;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.result.reasoning.ReasoningPreOutput;
import com.uniovi.rag.application.service.runtime.reasoning.ReasoningStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TracedReasoningStrategy}.
 */
class TracedReasoningStrategyTest {

    private ReasoningStrategy delegate;
    private TracedReasoningStrategy traced;

    @BeforeEach
    void setUp() {
        delegate = mock(ReasoningStrategy.class);
        ObservabilitySupport obs = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedReasoningStrategy(delegate, obs);
    }

    @Test
    void runPreStep_delegates() {
        ReasoningPreOutput pre = ReasoningPreOutput.of("plan");
        when(delegate.runPreStep(anyString(), any(), any(), anyString())).thenReturn(pre);

        JSONObject ner = new JSONObject();
        assertEquals(pre, traced.runPreStep("q", QueryType.SUMMARIZE_MEETING, ner, "expanded"));
        verify(delegate).runPreStep(eq("q"), eq(QueryType.SUMMARIZE_MEETING), any(JSONObject.class), eq("expanded"));
    }

    @Test
    void runPostStep_delegates() {
        PostStepOutput post = PostStepOutput.verified("ok");
        when(delegate.runPostStep(anyString(), anyString(), anyString())).thenReturn(post);

        assertEquals(post, traced.runPostStep("q", "ctx", "draft"));
        verify(delegate).runPostStep("q", "ctx", "draft");
    }

    @Test
    void runPreStep_nullObservability_delegatesOnly() {
        ReasoningStrategy del = mock(ReasoningStrategy.class);
        TracedReasoningStrategy noObs = new TracedReasoningStrategy(del, null);
        ReasoningPreOutput pre = ReasoningPreOutput.of("x");
        when(del.runPreStep(anyString(), any(), any(), anyString())).thenReturn(pre);

        assertEquals(pre, noObs.runPreStep("q", null, null, "e"));
    }
}
