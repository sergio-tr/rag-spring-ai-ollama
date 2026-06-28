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

class MetadataCountAndExplainToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataCountAndExplainTool tool;

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
        tool = new MetadataCountAndExplainTool(chatClient, retriever, extractor, llmCache);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("cuántos y explica", QueryType.COUNT_AND_EXPLAIN, null));
        assertNotNull(result);
        assertNotNull(result.result());
        assertEquals("MetadataCountAndExplainTool", result.source());
    }

    @Test
    void fdCe02_exact21_returnsDeterministicNegativeWith21() {
        ToolResult result =
                tool.execute(
                        ToolExecutionContext.of(
                                "¿En qué reuniones hubo exactamente 21 asistentes y qué se decidió en esa reunión?",
                                QueryType.COUNT_AND_EXPLAIN,
                                null));

        assertThat(result.result()).contains("21");
        assertThat(result.result().toLowerCase())
                .contains("no existen registros")
                .doesNotContain("¿a qué acta");
    }
}
