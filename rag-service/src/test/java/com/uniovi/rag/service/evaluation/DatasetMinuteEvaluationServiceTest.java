package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class DatasetMinuteEvaluationServiceTest {

    private DatasetMinuteEvaluationService service;

    @BeforeEach
    void setUp() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryService queryService = mock(QueryService.class);
        service = new DatasetMinuteEvaluationService(
                featureConfig, new RagImplementationProperties(), chatClient, documentService, queryService, false);
    }

    @Test
    void getQuestionsAndAnswers_returnsNonNullMap() {
        Map<String, String> qa = service.getQuestionsAndAnswers();
        assertNotNull(qa);
    }

    @Test
    void getQuestionsAndAnswers_doesNotThrow() {
        assertDoesNotThrow(() -> service.getQuestionsAndAnswers());
    }
}
