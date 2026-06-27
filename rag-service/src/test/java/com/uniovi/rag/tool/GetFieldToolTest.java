package com.uniovi.rag.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GetFieldToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private GetFieldTool tool;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.clientWithUserPromptReturning("No field found.");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
        tool = new GetFieldTool(chatClient, retriever, extractor);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("fecha del acta", QueryType.GET_FIELD, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("GetFieldTool", result.source());
    }

    @Test
    void execute_attendees_usesCombinedDocumentText() {
        String actaText = """
                Fecha: 25 de febrero de 2026
                • Alice Example
                • Bob Example
                • Carol Example
                """;
        Document doc = new Document(
                actaText,
                Map.of("document_id", "doc-acta-5", "fileName", "ACTA 5.pdf"));
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of(doc));
        doNothing().when(retriever).restoreDefaultSettings();
        doNothing().when(retriever).setTopK(anyInt());
        doNothing().when(retriever).setSimilarityThreshold(anyDouble());
        when(extractor.extractAttendees(anyString()))
                .thenReturn(List.of("Alice Example", "Bob Example", "Carol Example"));
        when(extractor.extractDate(anyString())).thenReturn("25 de febrero de 2026");

        JSONObject ner = new JSONObject();
        ner.put("date", "25 de febrero de 2026");
        ner.put("field", "attendees");
        ToolResult result = tool.execute(
                ToolExecutionContext.of(
                        "dime los participantes del acta del 25 de febrero de 2026",
                        QueryType.GET_FIELD,
                        ner));

        assertNotNull(result.result());
        assertTrue(result.result().contains("Alice Example"));
        assertTrue(result.result().contains("Bob Example"));
        assertTrue(result.result().contains("Carol Example"));
        assertTrue(result.result().contains("ACTA 5.pdf"));
        assertTrue(result.result().contains("3 en total"));
    }
}
