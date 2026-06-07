package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.evaluation.fixtures.SimpleMinuteEvaluationService;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Covers {@link AbstractMinuteEvaluationService} via concrete subclass. */
class AbstractMinuteEvaluationServiceTest {

    @Test
    void simpleMinuteEvaluationService_isInstanceOfAbstractMinuteEvaluationService() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryExecutionService queryService = mock(QueryExecutionService.class);
        SimpleMinuteEvaluationService service = new SimpleMinuteEvaluationService(
                featureConfig, new RagImplementationProperties(), chatClient, documentService, queryService, false);
        assertNotNull(service);
        assertTrue(service instanceof AbstractMinuteEvaluationService);
    }
}
