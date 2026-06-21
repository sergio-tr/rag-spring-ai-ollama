package com.uniovi.rag.application.service.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.evaluation.preset.LabBenchmarkExecutionContext;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.evaluation.fixtures.SimpleMinuteEvaluationService;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers {@link AbstractEvaluationService} via concrete subclass. */
class AbstractEvaluationServiceTest {

    @Test
    void simpleMinuteEvaluationService_isInstanceOfAbstractEvaluationService() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryExecutionService queryService = mock(QueryExecutionService.class);
        SimpleMinuteEvaluationService service = new SimpleMinuteEvaluationService(
                featureConfig, new RagImplementationProperties(), chatClient, documentService, queryService, false);
        assertNotNull(service);
        assertTrue(service instanceof AbstractEvaluationService);
    }

    @Test
    void loadDataWithConfiguration_skipsClasspathReloadWhenLabContextActive() throws Exception {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        RagFeatureConfiguration customConfig = new RagFeatureConfiguration();
        customConfig.setUseRetrieval(false);
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryExecutionService queryService = mock(QueryExecutionService.class);
        EvaluationServiceFactory factory = mock(EvaluationServiceFactory.class);
        when(factory.createDocumentService(customConfig)).thenReturn(documentService);

        SimpleMinuteEvaluationService service =
                new SimpleMinuteEvaluationService(
                        featureConfig, new RagImplementationProperties(), chatClient, documentService, queryService, true);
        service.setEvaluationServiceFactory(factory);

        UUID runId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        try (AutoCloseable ignored =
                LabBenchmarkExecutionContext.openLab(
                        new ObjectMapper().createObjectNode(),
                        runId,
                        projectId,
                        List.of(snapshotId),
                        "CHUNK",
                        "P1",
                        true)) {
            service.loadDataWithConfiguration(customConfig);
        }

        verify(documentService, never()).clearDatabase();
        verify(factory, never()).createDocumentService(customConfig);
    }
}
