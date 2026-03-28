package com.uniovi.rag.service.evaluation;

import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.service.document.DocumentService;
import com.uniovi.rag.service.query.QueryService;
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
        QueryService queryService = mock(QueryService.class);
        service = new SimpleMinuteEvaluationService(
                featureConfig, new RagImplementationProperties(), chatClient, documentService, queryService, false);
    }

    @Test
    void getQuestionsAndAnswers_returnsNonEmptyMap() {
        Map<String, String> qa = service.getQuestionsAndAnswers();
        assertNotNull(qa);
        assertFalse(qa.isEmpty());
    }

    @Test
    void getQuestionsAndAnswers_containsQA1AndQA2Entries() {
        Map<String, String> qa = service.getQuestionsAndAnswers();
        assertTrue(qa.containsKey("¿Qué secciones comparten todas las actas?"));
        assertTrue(qa.containsKey("¿En cuántas actas se realizaron propuestas?"));
        assertEquals(SimpleMinuteEvaluationService.QA1.size() + SimpleMinuteEvaluationService.QA2.size(), qa.size());
    }

    @Test
    void getQuestionsAndAnswers_returnsExpectedAnswerForSampleQuestion() {
        Map<String, String> qa = service.getQuestionsAndAnswers();
        String answer = qa.get("¿Cuántas reuniones se realizaron en el año 2025?");
        assertNotNull(answer);
        assertTrue(answer.contains("2025") || answer.contains("Tres"));
    }
}
