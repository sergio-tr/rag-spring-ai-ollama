package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReferenceBundleMinuteEvaluationServiceTest {

    @Test
    void constructsAndReportsDataNotLoadedInitially() {
        ReferenceBundleMinuteEvaluationService svc =
                new ReferenceBundleMinuteEvaluationService(
                        new RagFeatureConfiguration(),
                        new RagImplementationProperties(),
                        mock(ChatClient.class),
                        mock(DocumentService.class),
                        mock(QueryExecutionService.class),
                        false);
        assertThat(svc.isEvaluationDataLoaded()).isFalse();
    }
}
