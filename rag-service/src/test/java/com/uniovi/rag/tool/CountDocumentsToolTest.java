package com.uniovi.rag.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class CountDocumentsToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private CountDocumentsTool tool;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.clientWithUserPromptReturning("No meeting minutes match.");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
        tool = new CountDocumentsTool(chatClient, retriever, extractor);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("¿Cuántos documentos?", QueryType.COUNT_DOCUMENTS, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("CountDocumentsTool", result.source());
    }

    @Test
    void execute_withNer_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("count", QueryType.COUNT_DOCUMENTS, new JSONObject()));
        assertNotNull(result);
        assertEquals("CountDocumentsTool", result.source());
    }
}
