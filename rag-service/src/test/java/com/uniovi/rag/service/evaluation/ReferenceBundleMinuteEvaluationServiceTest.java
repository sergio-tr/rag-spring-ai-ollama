package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ReferenceBundleMinuteEvaluationServiceTest {

    @Test
    void getQuestionsAndAnswers_isUnsupportedInProductionBean() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryService queryService = mock(QueryService.class);
        ReferenceBundleMinuteEvaluationService svc =
                new ReferenceBundleMinuteEvaluationService(
                        featureConfig,
                        new RagImplementationProperties(),
                        chatClient,
                        documentService,
                        queryService,
                        false);
        assertThrows(UnsupportedOperationException.class, svc::getQuestionsAndAnswers);
    }

    @Test
    void legacyEvaluatePaths_throw() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryService queryService = mock(QueryService.class);
        ReferenceBundleMinuteEvaluationService svc =
                new ReferenceBundleMinuteEvaluationService(
                        featureConfig,
                        new RagImplementationProperties(),
                        chatClient,
                        documentService,
                        queryService,
                        false);
        assertThrows(UnsupportedOperationException.class, svc::evaluate);
        assertThrows(
                UnsupportedOperationException.class,
                () -> svc.evaluateWithConfiguration(featureConfig, new RagImplementationProperties()));
        assertThrows(UnsupportedOperationException.class, svc::evaluateAllConfigurations);
    }
}
