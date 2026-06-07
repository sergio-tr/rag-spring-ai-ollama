package com.uniovi.rag.infrastructure.observability;

import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.application.service.evaluation.EvaluationTestFixtures;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.evaluation.EvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TracedEvaluationServiceTest {

    private EvaluationService delegate;
    private ObservabilitySupport observability;
    private TracedEvaluationService traced;

    @BeforeEach
    void setUp() {
        delegate = mock(EvaluationService.class);
        observability = mock(ObservabilitySupport.class);
        traced = new TracedEvaluationService(delegate, observability);
        when(observability.recordTimer(anyString(), any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(1);
            return supplier.get();
        });
        when(observability.runWithSpan(anyString(), any(), anyString(), any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(3);
            return supplier.get();
        });
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(2);
            runnable.run();
            return null;
        })
                .when(observability)
                .runWithSpan(anyString(), any(), any(Runnable.class));
    }

    @Test
    void evaluateTypedLlm_delegates() {
        LlmJudgeEvaluationBatchResult result = EvaluationTestFixtures.emptyLlmBatch();
        RagFeatureConfiguration config = mock(RagFeatureConfiguration.class);
        RagImplementationProperties impl = mock(RagImplementationProperties.class);
        when(delegate.evaluateWithConfigurationForLlmReaderQuestions(config, impl, List.of(), null))
                .thenReturn(result);
        assertEquals(
                result,
                traced.evaluateWithConfigurationForLlmReaderQuestions(config, impl, List.of(), null));
        verify(delegate).evaluateWithConfigurationForLlmReaderQuestions(config, impl, List.of(), null);
    }

    @Test
    void loadData_delegates() {
        traced.loadData();
        verify(delegate).loadData();
    }
}
