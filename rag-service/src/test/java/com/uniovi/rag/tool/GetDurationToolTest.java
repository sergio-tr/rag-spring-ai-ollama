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

class GetDurationToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private GetDurationTool tool;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.clientWithUserPromptReturning("No duration found.");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
        tool = new GetDurationTool(chatClient, retriever, extractor);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("duración de la reunión", QueryType.GET_DURATION, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("GetDurationTool", result.source());
    }
}
