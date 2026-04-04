package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetadataCountDocumentsToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataCountDocumentsTool tool;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        ChatClientTestSupport.stubUserPromptReturns(chatClient, "NONE");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
        MetadataLlmResponseCacheService llmCache = mock(MetadataLlmResponseCacheService.class);
        when(llmCache.getCachedResponse(anyString())).thenReturn("");
        tool = new MetadataCountDocumentsTool(chatClient, retriever, extractor, llmCache);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolExecutionContext ctx = ToolExecutionContext.of("¿Cuántos documentos hay?", QueryType.COUNT_DOCUMENTS, null);
        ToolResult result = tool.execute(ctx);
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataCountDocumentsTool", result.source());
    }

    @Test
    void execute_withNer_returnsToolResult() {
        JSONObject ner = new JSONObject();
        ToolExecutionContext ctx = ToolExecutionContext.of("count by date", QueryType.COUNT_DOCUMENTS, ner);
        ToolResult result = tool.execute(ctx);
        assertNotNull(result);
        assertNotNull(result.result());
    }
}
