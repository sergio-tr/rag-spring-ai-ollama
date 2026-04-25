package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for {@link EvaluationService}.
 */
class EvaluationServiceTest {

    @Test
    void interfaceMethods_canBeStubbed() {
        EvaluationService svc = mock(EvaluationService.class);

        when(svc.evaluate()).thenReturn(Map.of("k", "v"));
        when(svc.evaluateWithConfiguration(any(), any())).thenReturn(Map.of("a", 1));
        when(svc.evaluateAllConfigurations()).thenReturn(Map.of("x", Map.of("y", 2)));
        doNothing().when(svc).loadData();
        when(svc.getQuestionsAndAnswers()).thenReturn(Map.of("q", "a"));
        when(svc.isEvaluationDataLoaded()).thenReturn(true);

        assertThat(svc.evaluate()).containsEntry("k", "v");
        assertThat(svc.evaluateWithConfiguration(mock(RagFeatureConfiguration.class), mock(RagImplementationProperties.class)))
                .containsEntry("a", 1);
        assertThat(svc.evaluateAllConfigurations()).containsKey("x");
        svc.loadData();
        assertThat(svc.getQuestionsAndAnswers()).containsEntry("q", "a");
        assertThat(svc.isEvaluationDataLoaded()).isTrue();
    }
}
