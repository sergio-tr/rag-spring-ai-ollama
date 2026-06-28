package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.result.evaluation.LlmJudgeEvaluationBatchResult;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.evaluation.workbook.LlmReaderQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for {@link EvaluationService}.
 */
class EvaluationServiceTest {

    @Test
    void interfaceMethods_canBeStubbed() {
        EvaluationService svc = mock(EvaluationService.class);

        when(svc.evaluateWithConfigurationForLlmReaderQuestions(any(), any(), any()))
                .thenReturn(EvaluationTestFixtures.emptyLlmBatch());
        doNothing().when(svc).loadData();
        when(svc.isEvaluationDataLoaded()).thenReturn(true);
        when(svc.judgeQaAnswer(any(), any(), any())).thenReturn("ok");

        LlmJudgeEvaluationBatchResult batch =
                svc.evaluateWithConfigurationForLlmReaderQuestions(
                        mock(RagFeatureConfiguration.class),
                        mock(RagImplementationProperties.class),
                        List.of());
        assertThat(batch.results()).isEmpty();
        svc.loadData();
        assertThat(svc.isEvaluationDataLoaded()).isTrue();
        assertThat(svc.judgeQaAnswer("q", "g", "a")).isEqualTo("ok");
    }
}
