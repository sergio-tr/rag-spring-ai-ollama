package com.uniovi.rag.tool;

import com.uniovi.rag.model.QueryType;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FindParagraphToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private FindParagraphTool tool;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.clientWithUserPromptReturning("No relevant paragraph found.");
        retriever = mock(ContextRetriever.class);
        extractor = mock(DocumentContentExtractor.class);
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());
        tool = new FindParagraphTool(chatClient, retriever, extractor);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("find paragraph about X", QueryType.FIND_PARAGRAPH, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("FindParagraphTool", result.source());
    }
}
