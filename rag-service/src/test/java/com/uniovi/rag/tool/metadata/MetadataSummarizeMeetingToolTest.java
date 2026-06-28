package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.application.service.runtime.document.extraction.DocumentContentExtractor;
import com.uniovi.rag.application.service.runtime.retrieval.ContextRetriever;
import com.uniovi.rag.testsupport.ChatClientTestSupport;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MetadataSummarizeMeetingToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataSummarizeMeetingTool tool;

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
        tool = new MetadataSummarizeMeetingTool(chatClient, retriever, extractor, llmCache);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("resumen reunión", QueryType.SUMMARIZE_MEETING, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataSummarizeMeetingTool", result.source());
    }

    @Test
    void fdSm02_year2030_returnsDeterministicNegativeWithoutWrongActa() {
        when(retriever.retrieve(anyString())).thenReturn(List.of());
        when(retriever.retrieveWithMetadataFilters(anyString(), any(JSONObject.class))).thenReturn(List.of());

        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "Resume el acta del año 2030.", QueryType.SUMMARIZE_MEETING, null));

        String answer = result.result();
        assertThat(answer).contains("2030");
        assertThat(answer.toLowerCase()).contains("no se puede", "resumen");
        assertThat(answer.length())
                .isGreaterThanOrEqualTo(StructuredMinuteMetadataSupport.summarizeMeetingEvaluatorMinLength());
        assertThat(answer.toLowerCase()).doesNotContain("25/02/2026", "25/02/2025", "acta 5", "acta 1");
    }
}
