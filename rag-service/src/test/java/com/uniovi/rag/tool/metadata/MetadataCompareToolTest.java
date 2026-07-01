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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MetadataCompareToolTest {

    private ChatClient chatClient;
    private ContextRetriever retriever;
    private DocumentContentExtractor extractor;
    private MetadataCompareTool tool;

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
        when(llmCache.getCachedResponse(anyString(), anyString())).thenReturn("NONE");
        tool = new MetadataCompareTool(chatClient, retriever, extractor, llmCache);
    }

    @Test
    void execute_emptyRetrieval_returnsToolResult() {
        ToolResult result = tool.execute(ToolExecutionContext.of("comparar", QueryType.COMPARE, null));
        assertThat(result).isNotNull();
        assertThat(result.result()).isNotNull();
        assertThat(result.source()).isEqualTo("MetadataCompareTool");
    }

    @Test
    void proposalsFebVsAug_routesToCompare() {
        assertThat(
                        com.uniovi.rag.application.service.runtime.query.ClassifierOverrides.apply(
                                "Compara la cantidad de propuestas presentadas en febrero y agosto.",
                                QueryType.COUNT_DOCUMENTS))
                .isEqualTo(QueryType.COMPARE);
    }
}
