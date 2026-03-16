package com.uniovi.rag.tool;

import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DecisionExtractionToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private DecisionExtractionTool tool;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
        var callSpec = mock(org.springframework.ai.chat.client.CallResponseSpec.class);
        var promptSpec = mock(org.springframework.ai.chat.client.PromptSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(callSpec);
        when(callSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("No decisions found.");
        tool = new DecisionExtractionTool(chatClient, retriever, extractor);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("decisiones tomadas", QueryType.DECISION_EXTRACTION, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("DecisionExtractionTool", result.source());
    }
}
