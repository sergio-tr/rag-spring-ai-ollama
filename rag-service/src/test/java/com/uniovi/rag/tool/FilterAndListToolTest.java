package com.uniovi.rag.tool;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class FilterAndListToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private FilterAndListTool tool;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.clientWithUserPromptReturning("No results match the filters.");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
        tool = new FilterAndListTool(chatClient, retriever, extractor);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("listar actas de 2025", QueryType.FILTER_AND_LIST, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("FilterAndListTool", result.source());
    }
}
