package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.application.service.knowledge.document.DocumentService;
import com.uniovi.rag.application.service.evaluation.fixtures.SimpleMinuteEvaluationService;
import com.uniovi.rag.application.service.runtime.execution.QueryExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SimpleMinuteEvaluationServiceTest {

    private SimpleMinuteEvaluationService service;

    @BeforeEach
    void setUp() {
        RagFeatureConfiguration featureConfig = new RagFeatureConfiguration();
        ChatClient chatClient = mock(ChatClient.class);
        DocumentService documentService = mock(DocumentService.class);
        QueryExecutionService queryService = mock(QueryExecutionService.class);
        service = new SimpleMinuteEvaluationService(
                featureConfig, new RagImplementationProperties(), chatClient, documentService, queryService, false);
    }

    @Test
    void fixtureQuestionsAndAnswers_returnsNonEmptyMap() {
        Map<String, String> qa = service.fixtureQuestionsAndAnswers();
        assertNotNull(qa);
        assertFalse(qa.isEmpty());
    }

    @Test
    void fixtureQuestionsAndAnswers_containsQA1AndQA2Entries() {
        Map<String, String> qa = service.fixtureQuestionsAndAnswers();
        assertTrue(qa.containsKey("¿Qué secciones comparten todas las actas?"));
        assertTrue(qa.containsKey("¿En cuántas actas se realizaron propuestas?"));
        assertEquals(SimpleMinuteEvaluationService.QA1.size() + SimpleMinuteEvaluationService.QA2.size(), qa.size());
    }

    @Test
    void fixtureQuestionsAndAnswers_returnsExpectedAnswerForSampleQuestion() {
        Map<String, String> qa = service.fixtureQuestionsAndAnswers();
        String answer = qa.get("¿Cuántas reuniones se realizaron en el año 2025?");
        assertNotNull(answer);
        assertTrue(answer.contains("2025") || answer.contains("Tres"));
    }
}
