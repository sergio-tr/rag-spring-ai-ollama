package com.uniovi.rag.observability;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.evaluation.EvaluationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracedEvaluationService}.
 */
class TracedEvaluationServiceTest {

    private EvaluationService delegate;
    private ObservabilitySupport observability;
    private TracedEvaluationService traced;

    @BeforeEach
    void setUp() {
        delegate = mock(EvaluationService.class);
        observability = new ObservabilitySupport(new SimpleTracer(), new SimpleMeterRegistry());
        traced = new TracedEvaluationService(delegate, observability);
    }

    @Test
    void evaluate_delegatesAndReturnsResult() {
        Map<String, Object> result = Map.of("score", 0.85);
        when(delegate.evaluate()).thenReturn(result);
        assertEquals(result, traced.evaluate());
        verify(delegate).evaluate();
    }

    @Test
    void evaluateWithConfiguration_delegates() {
        RagFeatureConfiguration config = new RagFeatureConfiguration();
        RagImplementationProperties impl = new RagImplementationProperties();
        Map<String, Object> result = Map.of("score", 0.9);
        when(delegate.evaluateWithConfiguration(any(), any())).thenReturn(result);
        assertEquals(result, traced.evaluateWithConfiguration(config, impl));
        verify(delegate).evaluateWithConfiguration(config, impl);
    }

    @Test
    void evaluateAllConfigurations_delegates() {
        Map<String, Map<String, Object>> result = Map.of("c1", Map.of("score", 0.8));
        when(delegate.evaluateAllConfigurations()).thenReturn(result);
        assertEquals(result, traced.evaluateAllConfigurations());
        verify(delegate).evaluateAllConfigurations();
    }

    @Test
    void loadData_delegates() {
        doNothing().when(delegate).loadData();
        traced.loadData();
        verify(delegate).loadData();
    }
}
