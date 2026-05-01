package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractEvaluationService} via concrete subclass. */
class AbstractEvaluationServiceTest {

    @Test
    void simpleMinuteEvaluationService_isInstanceOfAbstractEvaluationService() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryService queryService = mock(QueryService.class);
        SimpleMinuteEvaluationService service = new SimpleMinuteEvaluationService(
                featureConfig, new RagImplementationProperties(), chatClient, documentService, queryService, false);
        assertNotNull(service);
        assertTrue(service instanceof AbstractEvaluationService);
    }
}
